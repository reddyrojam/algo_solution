package p2

import com.google.gson.Gson
import com.rabbitmq.client.*
import kotlinx.coroutines.*
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import p2.DatabaseService
import p2.Insight
import p2.NotificationEvent
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class InsightTestProcessingService(
    private val cleanupIntervalSeconds: Long,
    private val insightRetentionMillis: Long,
    private val maxInsightsPerCamera: Int
) {
    private val insights = ConcurrentHashMap<String, MutableList<Insight>>()
    private val mqttClient = MqttClient("tcp://localhost:1883", MqttAsyncClient.generateClientId(), MemoryPersistence())
    private val gson = Gson()
    private val alarmTriggered = AtomicBoolean(false)

    init {
        mqttClient.connect()
        DatabaseService.startDatabase()
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
        DatabaseService.insertInsight(insight)
        checkRulesAndTriggerNotification(insight)
    }

    private fun checkRulesAndTriggerNotification(insight: Insight) {
        if (insight.state == "1") {
            val event = NotificationEvent(
                code = "ALERT_01",
                severity = "HIGH",
                zoneUid = insight.zoneUid,
                shortMessage = "State 1 detected",
                recommendedAction = "Investigate immediately",
                detailedMessage = "An insight with state 1 has been detected in zone ${insight.zoneUid}.",
                rule = "state == 1",
                params = gson.toJson(insight)
            )
            DatabaseService.insertNotificationEvent(event)
        }
    }

    private fun mergeInsights(): String {
        val data = mutableMapOf<String, List<Map<String, Any>>>()
        val camUidMapping = mutableMapOf<String, String>()

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

    private fun triggerAlarmIfNeeded() {
        val entityManager = DatabaseService.entityManager
        val rule = "SELECT COUNT(i) FROM Insight i WHERE i.threatCategory = 3"
        val result = entityManager.createQuery(rule).singleResult as Long
        if (result > 0 && alarmTriggered.compareAndSet(false, true)) {
            val notificationEvent = NotificationEvent(
                code = "ALARM",
                severity = "HIGH",
                zoneUid = "ZONE_A",
                shortMessage = "Intrusion detected",
                recommendedAction = "Investigate immediately",
                detailedMessage = "Intrusion detected in Zone A",
                rule = "INTRUSION_RULE"
            )
            DatabaseService.insertNotificationEvent(notificationEvent)
            println("Alarm Triggered: $notificationEvent")

            // Sleep for 5 minutes (300000 milliseconds)
            Thread.sleep(300000)

            // Reset the alarm trigger
            alarmTriggered.set(false)
        }
    }

    private fun publishToMQTT(topic: String, payload: String) {
        val message = MqttMessage(payload.toByteArray())
        mqttClient.publish(topic, message)
    }

    private fun mergeAndPublish() {
        val mergedOutput = mergeInsights()
        publishToMQTT("POS", mergedOutput)
        triggerAlarmIfNeeded()
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
            insightList.removeIf { currentTime - (it.timestamp ?: 0L) > insightRetentionMillis }
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
