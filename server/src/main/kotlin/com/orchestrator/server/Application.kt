package com.orchestrator.server

import com.orchestrator.server.config.ServerConfig
import com.orchestrator.server.dashboard.DashboardStateAggregator
import com.orchestrator.server.handler.MessageHandler
import com.orchestrator.server.route.apiRoutes
import com.orchestrator.server.route.webSocketRoutes
import com.orchestrator.server.session.HostCodeManager
import com.orchestrator.server.session.NodeSessionManager
import com.orchestrator.server.webhook.WebhookManager
import com.orchestrator.common.model.ContainerInfo
import com.orchestrator.common.model.ContainerStatus
import com.orchestrator.common.model.NodeInfo
import com.orchestrator.common.model.Permission
import com.orchestrator.common.model.PortMapping
import com.orchestrator.common.network.NetworkDiagnostics
import com.orchestrator.common.security.TlsCertificateGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import com.orchestrator.common.util.AppJson
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    val enableTls = args.contains("--tls")
    val port = args.indexOf("--port").let { if (it >= 0) args.getOrNull(it + 1)?.toIntOrNull() else null } ?: 8080
    val config = ServerConfig(port = port, enableTls = enableTls)
    val logger = LoggerFactory.getLogger("Application")

    // Network diagnostics
    NetworkDiagnostics.printDiagnostics()

    val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val hostCodeManager = HostCodeManager()
    val nodeSessionManager = NodeSessionManager()
    val dashboardAggregator = DashboardStateAggregator(nodeSessionManager)
    val webhookManager = WebhookManager(serverScope)
    val messageHandler = MessageHandler(hostCodeManager, nodeSessionManager, dashboardAggregator, webhookManager = webhookManager)

    // Register host as a node
    try {
        val osName = System.getProperty("os.name", "").lowercase()
        val dockerHost = System.getenv("DOCKER_HOST") ?: when {
            osName.contains("win") -> "npipe:////./pipe/docker_engine"
            osName.contains("mac") || osName.contains("darwin") -> {
                val colima = "${System.getProperty("user.home")}/.colima/default/docker.sock"
                if (java.io.File(colima).exists()) "unix://$colima" else "unix:///var/run/docker.sock"
            }
            else -> "unix:///var/run/docker.sock"
        }
        val dockerConfig = com.github.dockerjava.core.DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(dockerHost).build()
        val httpClient = com.github.dockerjava.httpclient5.ApacheDockerHttpClient.Builder()
            .dockerHost(java.net.URI.create(dockerHost))
            .connectionTimeout(java.time.Duration.ofSeconds(5))
            .responseTimeout(java.time.Duration.ofSeconds(30))
            .build()
        val dockerClient = com.github.dockerjava.core.DockerClientImpl.getInstance(dockerConfig, httpClient)

        val dockerVersion = dockerClient.versionCmd().exec().version ?: "unknown"
        val containers = dockerClient.listContainersCmd().withShowAll(true).exec().map { c ->
            ContainerInfo(
                id = c.id.take(12),
                name = c.names?.firstOrNull()?.removePrefix("/") ?: "unnamed",
                image = c.image ?: "unknown",
                status = when (c.state?.lowercase()) {
                    "running" -> ContainerStatus.RUNNING; "exited" -> ContainerStatus.EXITED
                    "paused" -> ContainerStatus.PAUSED; else -> ContainerStatus.UNKNOWN
                },
                state = c.state ?: "unknown",
                ports = c.ports?.map { p -> PortMapping(p.privatePort ?: 0, p.publicPort, p.type?.toString()?.lowercase() ?: "tcp", p.ip) } ?: emptyList(),
                createdAt = c.created ?: 0L,
                uptime = c.status ?: ""
            )
        }

        val hostName = try { java.net.InetAddress.getLocalHost().hostName } catch (_: Exception) { "Host" }
        val hostNodeId = "host-${java.util.UUID.randomUUID().toString().take(6)}"
        val hostNodeInfo = NodeInfo(
            nodeId = hostNodeId, hostName = "$hostName (Host)",
            os = System.getProperty("os.name", "unknown"), dockerVersion = dockerVersion,
            containers = containers, permission = Permission.FULL_CONTROL
        )
        dashboardAggregator.setHostNode(hostNodeId, hostNodeInfo)

        // Periodically refresh host containers
        serverScope.launch(Dispatchers.IO) {
            while (true) {
                kotlinx.coroutines.delay(5000)
                try {
                    val updated = dockerClient.listContainersCmd().withShowAll(true).exec().map { c ->
                        ContainerInfo(
                            id = c.id.take(12),
                            name = c.names?.firstOrNull()?.removePrefix("/") ?: "unnamed",
                            image = c.image ?: "unknown",
                            status = when (c.state?.lowercase()) {
                                "running" -> ContainerStatus.RUNNING; "exited" -> ContainerStatus.EXITED
                                "paused" -> ContainerStatus.PAUSED; else -> ContainerStatus.UNKNOWN
                            },
                            state = c.state ?: "unknown",
                            ports = c.ports?.map { p -> PortMapping(p.privatePort ?: 0, p.publicPort, p.type?.toString()?.lowercase() ?: "tcp", p.ip) } ?: emptyList(),
                            createdAt = c.created ?: 0L
                        )
                    }
                    dashboardAggregator.updateHostContainers(hostNodeInfo.copy(containers = updated))
                    dashboardAggregator.broadcastClusterState()
                } catch (_: Exception) {}
            }
        }
        logger.info("Host registered as node: $hostNodeId")
    } catch (e: Exception) {
        logger.warn("Could not register host as Docker node: ${e.message}")
    }

    val hostCode = hostCodeManager.generateCode()
    logger.info("=== DRO Server ===")
    logger.info("Host Code: $hostCode")

    fun Application.configureServer() {
        install(ContentNegotiation) { json(AppJson) }
        install(WebSockets) {
            pingPeriod = config.pingIntervalSeconds.seconds
            timeout = config.timeoutSeconds.seconds
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
        install(CallLogging)

        routing {
            get("/") {
                call.respondText("DRO Server - Host Code: $hostCode")
            }
            get("/health") {
                call.respondText("OK")
            }
            get("/status") {
                val nodes = nodeSessionManager.getConnectedNodeCount()
                call.respondText("Connected nodes: $nodes")
            }
            get("/network") {
                val info = NetworkDiagnostics.getNetworkInfo()
                call.respond(mapOf(
                    "hostname" to info.hostname,
                    "localIp" to info.localIp,
                    "publicIp" to (info.publicIp ?: "unavailable"),
                    "interfaces" to info.allAddresses.joinToString(", "),
                    "tlsEnabled" to config.enableTls.toString(),
                    "port" to config.port.toString(),
                    "sslPort" to config.sslPort.toString()
                ))
            }
            webSocketRoutes(messageHandler, dashboardAggregator, nodeSessionManager)
            apiRoutes(nodeSessionManager, hostCodeManager, messageHandler, webhookManager, dashboardAggregator)
        }
    }

    if (config.enableTls) {
        val tlsConfig = TlsCertificateGenerator.generateSelfSigned()
        logger.info("TLS enabled - HTTPS on port ${config.sslPort}")
        logger.info("Keystore: ${tlsConfig.keyStorePath.absolutePath}")

        val serverHost = config.host
        val serverPort = config.port
        val sslPort = config.sslPort
        embeddedServer(Netty, configure = {
            connector {
                this.host = serverHost
                this.port = serverPort
            }
            sslConnector(
                keyStore = tlsConfig.keyStore,
                keyAlias = "orchestrator",
                keyStorePassword = { tlsConfig.keyStorePassword },
                privateKeyPassword = { tlsConfig.keyStorePassword }
            ) {
                this.host = serverHost
                this.port = sslPort
            }
        }) {
            configureServer()
        }.also {
            logger.info("Server ready. Share host code: $hostCode")
            logger.info("Clients can connect via wss:// on port ${config.sslPort}")
            it.start(wait = true)
        }
    } else {
        logger.info("Server starting on ${config.host}:${config.port} (no TLS)")
        embeddedServer(Netty, port = config.port, host = config.host) {
            configureServer()
        }.also {
            logger.info("Server ready. Share host code: $hostCode")
            it.start(wait = true)
        }
    }
}
