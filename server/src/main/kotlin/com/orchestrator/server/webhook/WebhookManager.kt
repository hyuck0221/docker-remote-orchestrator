package com.orchestrator.server.webhook

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class WebhookConfig(
    val id: String,
    val url: String,
    val events: List<String>
)

@Serializable
data class WebhookPayload(
    val event: String,
    val nodeId: String,
    val containerId: String,
    val containerName: String,
    val detail: String,
    val timestamp: Long = System.currentTimeMillis()
)

class WebhookManager(private val scope: CoroutineScope) {

    private val logger = LoggerFactory.getLogger(WebhookManager::class.java)
    private val webhooks = ConcurrentHashMap<String, WebhookConfig>()
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
    private val json = Json { prettyPrint = false; encodeDefaults = true }

    fun addWebhook(url: String, events: List<String>): String {
        val id = UUID.randomUUID().toString().take(8)
        webhooks[id] = WebhookConfig(id = id, url = url, events = events)
        logger.info("Webhook added: $id -> $url (events: $events)")
        return id
    }

    fun removeWebhook(id: String) {
        webhooks.remove(id)
        logger.info("Webhook removed: $id")
    }

    fun listWebhooks(): List<WebhookConfig> = webhooks.values.toList()

    fun notify(event: String, nodeId: String, containerId: String, containerName: String, detail: String) {
        val payload = WebhookPayload(
            event = event,
            nodeId = nodeId,
            containerId = containerId,
            containerName = containerName,
            detail = detail
        )
        val body = json.encodeToString(payload)

        webhooks.values
            .filter { event in it.events }
            .forEach { webhook ->
                scope.launch {
                    try {
                        val request = HttpRequest.newBuilder()
                            .uri(URI.create(webhook.url))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .timeout(Duration.ofSeconds(10))
                            .build()

                        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                        logger.debug("Webhook ${webhook.id} response: ${response.statusCode()}")
                    } catch (e: Exception) {
                        logger.warn("Webhook ${webhook.id} failed: ${e.message}")
                    }
                }
            }
    }
}
