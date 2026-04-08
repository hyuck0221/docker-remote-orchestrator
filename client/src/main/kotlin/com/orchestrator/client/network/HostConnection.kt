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
    private val backoffMultiplier: Double = 2.0
) {
    private val logger = LoggerFactory.getLogger(HostConnection::class.java)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var wsSession: WebSocketSession? = null
    private var connectionJob: Job? = null
    private val httpClient = HttpClient(CIO) {
        install(WebSockets)
        engine {
            https {
                if (useTls) {
                    // Trust self-signed certificates
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
            val scheme = if (useTls) "wss" else "ws"
            logger.info("Connecting via $scheme to $host:$port")

            while (isActive) {
                _connectionState.value = if (backoff == initialBackoffMs) {
                    ConnectionState.CONNECTING
                } else {
                    ConnectionState.RECONNECTING
                }

                try {
                    val block: suspend DefaultClientWebSocketSession.() -> Unit = {
                        wsSession = this
                        _connectionState.value = ConnectionState.CONNECTED
                        backoff = initialBackoffMs
                        logger.info("Connected to server $host:$port ($scheme)")

                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    val text = frame.readText()
                                    clientMessageHandler.handleMessage(text)
                                }
                                is Frame.Close -> {
                                    logger.info("Server closed connection")
                                    break
                                }
                                else -> {}
                            }
                        }
                    }

                    if (useTls) {
                        httpClient.wss(host = host, port = port, path = "/ws/node", block = block)
                    } else {
                        httpClient.webSocket(host = host, port = port, path = "/ws/node", block = block)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("Connection failed: ${e.message}")
                }

                wsSession = null
                _connectionState.value = ConnectionState.DISCONNECTED
                logger.info("Reconnecting in ${backoff}ms...")
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
}
