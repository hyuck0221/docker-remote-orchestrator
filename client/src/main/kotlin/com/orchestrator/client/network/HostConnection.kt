package com.orchestrator.client.network

import com.orchestrator.common.protocol.WsMessage
import com.orchestrator.common.security.TlsCertificateGenerator
import com.orchestrator.common.util.AppJson
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING
}

class HostConnection(
    private val clientMessageHandler: ClientMessageHandler,
    private val useTls: Boolean = false,
    private val initialBackoffMs: Long = 1000L,
    private val maxBackoffMs: Long = 30000L,
    private val backoffMultiplier: Double = 1.5,
    private val maxReconnectAttempts: Int = Int.MAX_VALUE,
    private val onConnected: (suspend () -> Unit)? = null,
    private val onDisconnectedPermanently: (suspend (reason: String) -> Unit)? = null
) {
    private val logger = LoggerFactory.getLogger(HostConnection::class.java)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var wsSession: WebSocketSession? = null
    private var connectionJob: Job? = null
    private val httpClient = HttpClient(CIO) {
        install(WebSockets) {
            pingIntervalMillis = 5_000
        }
        engine {
            requestTimeout = 0
            https {
                if (useTls) {
                    trustManager = object : javax.net.ssl.X509TrustManager {
                        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                    }
                }
            }
        }
    }

    fun connect(host: String, port: Int, scope: CoroutineScope) {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            var backoff = initialBackoffMs

            val parsed = parseEndpoint(host, port, useTls)
            val actualHost = parsed.host
            val actualPort = parsed.port
            val actualTls = parsed.tls

            val scheme = if (actualTls) "wss" else "ws"
            logger.info("Connecting via $scheme to $actualHost:$actualPort")

            // Create a TLS-capable client if needed.
            val client = if (actualTls && !useTls) {
                HttpClient(CIO) {
                    install(WebSockets) {
                        pingIntervalMillis = 5_000
                    }
                    engine {
                        requestTimeout = 0
                        https {
                            trustManager = object : javax.net.ssl.X509TrustManager {
                                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                            }
                        }
                    }
                }
            } else {
                httpClient
            }

            var reconnectAttempts = 0
            var serverClosedCleanly = false
            var wasConnected = false

            while (isActive) {
                _connectionState.value = if (reconnectAttempts == 0) {
                    ConnectionState.CONNECTING
                } else {
                    ConnectionState.RECONNECTING
                }

                wasConnected = false
                serverClosedCleanly = false

                try {
                    val block: suspend DefaultClientWebSocketSession.() -> Unit = {
                        wsSession = this
                        _connectionState.value = ConnectionState.CONNECTED
                        backoff = initialBackoffMs
                        wasConnected = true
                        logger.info("Connected to server $actualHost:$actualPort ($scheme)")

                        // Re-send JoinRequest on every (re)connection
                        onConnected?.invoke()

                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    val text = frame.readText()
                                    try {
                                        clientMessageHandler.handleMessage(text)
                                    } catch (e: Exception) {
                                        logger.error("Client handleMessage failed: ${e.message}", e)
                                    }
                                    // Only reset reconnect counter after receiving real data
                                    reconnectAttempts = 0
                                }
                                is Frame.Ping -> {
                                    send(Frame.Pong(frame.data))
                                }
                                is Frame.Close -> {
                                    logger.info("Server closed connection")
                                    serverClosedCleanly = true
                                    break
                                }
                                else -> {}
                            }
                        }
                    }

                    if (actualTls) {
                        client.wss(host = actualHost, port = actualPort, path = "/ws/node", block = block)
                    } else {
                        client.webSocket(host = actualHost, port = actualPort, path = "/ws/node", block = block)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("Connection failed: ${e.message}")
                }

                wsSession = null
                _connectionState.value = ConnectionState.DISCONNECTED

                // If server sent a clean Close frame, disconnect permanently
                if (serverClosedCleanly) {
                    logger.info("Host server shut down, disconnecting permanently")
                    onDisconnectedPermanently?.invoke("Host connection closed")
                    break
                }

                // Always increment reconnect attempts after a disconnect/failure
                reconnectAttempts++

                // If max reconnect attempts exceeded, give up
                if (reconnectAttempts >= maxReconnectAttempts) {
                    logger.warn("Max reconnect attempts ($maxReconnectAttempts) reached, giving up")
                    onDisconnectedPermanently?.invoke("Host connection lost")
                    break
                }

                logger.info("Reconnecting in ${backoff}ms... (attempt $reconnectAttempts/$maxReconnectAttempts)")
                delay(backoff)
                backoff = (backoff * backoffMultiplier).toLong().coerceAtMost(maxBackoffMs)
            }
        }
    }

    suspend fun send(message: WsMessage) {
        val session = wsSession ?: run {
            logger.warn("Cannot send message: not connected")
            return
        }
        try {
            val json = AppJson.encodeToString<WsMessage>(message)
            session.send(Frame.Text(json))
        } catch (e: Exception) {
            logger.error("Failed to send message", e)
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        wsSession = null
        _connectionState.value = ConnectionState.DISCONNECTED
        logger.info("Disconnected from server")
    }

    fun close() {
        disconnect()
        httpClient.close()
    }

    private data class Endpoint(val host: String, val port: Int, val tls: Boolean)

    private fun parseEndpoint(host: String, port: Int, defaultTls: Boolean): Endpoint {
        val trimmed = host.trim().trimEnd('/')
        return when {
            trimmed.startsWith("wss://") -> Endpoint(trimmed.removePrefix("wss://"), 443, true)
            trimmed.startsWith("https://") -> Endpoint(trimmed.removePrefix("https://"), 443, true)
            trimmed.startsWith("ws://") -> Endpoint(trimmed.removePrefix("ws://"), port, false)
            trimmed.startsWith("http://") -> Endpoint(trimmed.removePrefix("http://"), port, false)
            else -> Endpoint(trimmed, port, defaultTls)
        }
    }
}
