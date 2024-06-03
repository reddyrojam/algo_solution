import com.google.gson.Gson
import com.rabbitmq.client.*
import kotlinx.coroutines.*
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

data class Insight(
    val objectUid: String,
    val state: String,
    val positionX: Int,
    val positionY: Int,
    val timestamp: Long,
    val zoneUid: String,
    val sectorUid: String,
    val threatCategory: Int,
    val camUid: String
)

class InsightTestProcessingService(
    private val cleanupIntervalSeconds: Long,
    private val insightRetentionMillis: Long,
    private val maxInsightsPerCamera: Int
) {
    private val insights = ConcurrentHashMap<String, MutableList<Insight>>()
    private val mqttClient = MqttClient("tcp://localhost:1883", MqttAsyncClient.generateClientId(), MemoryPersistence())
    private val gson = Gson()

    init {
        mqttClient.connect()
    }

    fun processInsight(insight: Insight) {
        insights.compute(insight.camUid) { _, existingInsights ->
            val updatedInsights = existingInsights?.toMutableList() ?: mutableListOf()
            updatedInsights.add(insight)
            if (updatedInsights.size > maxInsightsPerCamera) {
                updatedInsights.removeAt(0)
            }
            updatedInsights
        }
    }

    private fun mergeInsights(): String {
        val data = mutableMapOf<String, List<Map<String, Any>>>()
        val camUidMapping = mutableMapOf<String, String>()

        // Create a copy of insights to avoid concurrent modification
        val insightsCopy = synchronized(insights) { insights.toMap() }

        insightsCopy.forEach { (camUid, insightList) ->
            val camData = insightList.map { insight ->
                mapOf(
                    "obUid" to insight.objectUid,
                    "state" to insight.state,
                    "poX" to insight.positionX,
                    "poY" to insight.positionY,
                    "tsmp" to insight.timestamp,
                    "zUid" to insight.zoneUid,
                    "sUid" to insight.sectorUid,
                    "tCat" to insight.threatCategory,
                    "cUid" to insight.camUid
                )
            }
            data[camUid] = camData
            camUidMapping[camUid] = camUid
        }

        val merged = mapOf(
            "command" to "POS",
            "payload" to mapOf(
                "data" to data,
                "meta" to mapOf(
                    "camUid" to camUidMapping,
                    "processedOn" to System.currentTimeMillis()
                )
            )
        )
        return gson.toJson(merged)
    }


    private fun publishToMQTT(topic: String, payload: String) {
        val message = MqttMessage(payload.toByteArray())
        mqttClient.publish(topic, message)
    }

    private fun mergeAndPublish() {
        val mergedOutput = mergeInsights()
//        publishToMQTT("POS", mergedOutput)
    }

    suspend fun startProcessing() = coroutineScope {
        launch {
            while (isActive) {
                delay(10 * 1000) // Publish every 10 seconds
                mergeAndPublish()
            }
        }
    }

    suspend fun startCleanup() = coroutineScope {
        launch {
            while (isActive) {
                delay(cleanupIntervalSeconds * 1000)
                cleanOldInsights()
            }
        }
    }

    private fun cleanOldInsights() {
        val currentTime = System.currentTimeMillis()
        insights.forEach { (_, insightList) ->
            insightList.removeIf { currentTime - it.timestamp > insightRetentionMillis }
        }
    }

    fun listenToRabbitMQ(queueName: String) {
        val factory = ConnectionFactory()
        factory.host = "localhost"
        val connection = factory.newConnection()
        val channel = connection.createChannel()

        channel.queueDeclare(queueName, false, false, false, null)
        val consumer = object : DefaultConsumer(channel) {
            @Throws(IOException::class)
            override fun handleDelivery(
                consumerTag: String,
                envelope: Envelope,
                properties: AMQP.BasicProperties,
                body: ByteArray
            ) {
                val message = String(body, StandardCharsets.UTF_8)
                val insight = gson.fromJson(message, Insight::class.java)
                processInsight(insight)
            }
        }
        channel.basicConsume(queueName, true, consumer)
    }
}

fun main() = runBlocking {
    val cleanupIntervalSeconds = 10L
    val insightRetentionMillis = 30000L
    val maxInsightsPerCamera = 10000

    val service = InsightTestProcessingService(
        cleanupIntervalSeconds,
        insightRetentionMillis,
        maxInsightsPerCamera
    )

    val rabbitMQQueueName = "Command"

    val rabbitJob = launch(Dispatchers.IO) {
        service.listenToRabbitMQ(rabbitMQQueueName)
    }

    val processJob = launch(Dispatchers.Default) {
        service.startProcessing()
    }

    val cleanupJob = launch(Dispatchers.Default) {
        service.startCleanup()
    }

    rabbitJob.join()
    processJob.join()
    cleanupJob.join()
}