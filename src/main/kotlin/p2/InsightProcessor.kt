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

class InsightProcessor {
    private val connectionFactory = ConnectionFactory().apply {
        host = "localhost"
    }
    private val executorService = Executors.newFixedThreadPool(10)  // Adjust the pool size as needed

    fun startListening() {
        val connection = connectionFactory.newConnection()
        val channel = connection.createChannel()
        val queueName = "insights"

        channel.queueDeclare(queueName, false, false, false, null)
        val deliverCallback = { consumerTag: String, delivery: Delivery ->
            val message = String(delivery.body, Charsets.UTF_8)
            executorService.submit {
                processMessage(message)
            }
        }
//        channel.basicConsume(queueName, true, deliverCallback, { consumerTag -> /* handle cancellation */ })

    }

    private fun processMessage(message: String) {
        try {
            val insight = parseMessageToInsight(message)
            DatabaseService.insertInsight(insight)
        } catch (ex: Exception) {
            // Handle exceptions appropriately
            ex.printStackTrace()
        }
    }

    private fun parseMessageToInsight(message: String): Insight {
        return Gson().fromJson(message, Insight::class.java)
    }

    fun stop() {
        executorService.shutdown()
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow()
            }
        } catch (ex: InterruptedException) {
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
