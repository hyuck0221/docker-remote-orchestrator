package com.orchestrator.client

import com.orchestrator.client.docker.*
import com.orchestrator.client.network.ClientMessageHandler
import com.orchestrator.client.network.HostConnection
import com.orchestrator.client.permission.PermissionManager
import com.orchestrator.common.model.NodeInfo
import com.orchestrator.common.network.NetworkDiagnostics
import com.orchestrator.common.protocol.WsMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.util.UUID

fun main(args: Array<String>) = runBlocking {
    val logger = LoggerFactory.getLogger("ClientMain")

    logger.info("=== Docker Remote Orchestrator Client ===")

    // Parse arguments
    val useTls = args.contains("--tls")
    val filteredArgs = args.filter { it != "--tls" }
    val serverHost = filteredArgs.getOrNull(0) ?: "localhost"
    val serverPort = filteredArgs.getOrNull(1)?.toIntOrNull() ?: if (useTls) 8443 else 8080
    val hostCode = filteredArgs.getOrNull(2) ?: run {
        logger.error("Usage: client <server-host> <server-port> <host-code> [--tls]")
        return@runBlocking
    }

    // Network diagnostics
    NetworkDiagnostics.printDiagnostics()
    val connectivity = NetworkDiagnostics.checkConnectivity(serverHost, serverPort)
    if (!connectivity.reachable) {
        logger.error("Cannot reach server at $serverHost:$serverPort - ${connectivity.error}")
        logger.error("Check: 1) Server is running  2) Firewall allows port $serverPort  3) Correct IP address")
        return@runBlocking
    }
    logger.info("Server reachable (${connectivity.latencyMs}ms)")

    // Initialize Docker
    val dockerClient = try {
        DockerClientFactory.create()
    } catch (e: Exception) {
        logger.error("Failed to connect to Docker engine", e)
        return@runBlocking
    }

    val containerService = ContainerService(dockerClient)
    val commandExecutor = ContainerCommandExecutor(dockerClient)
    val logStreamer = LogStreamer(dockerClient)
    val containerMonitor = ContainerMonitor(dockerClient, containerService)
    val permissionManager = PermissionManager()

    val nodeId = UUID.randomUUID().toString().take(8)
    val hostName = try {
        InetAddress.getLocalHost().hostName
    } catch (_: Exception) {
        "unknown"
    }
    val dockerVersion = containerService.getDockerVersion()

    logger.info("Node ID: $nodeId")
    logger.info("Host Name: $hostName")
    logger.info("Docker Version: $dockerVersion")

    // List local containers
    val initialContainers = containerService.listContainers()
    logger.info("Found ${initialContainers.size} containers:")
    initialContainers.forEach { c ->
        logger.info("  - ${c.name} (${c.image}) [${c.status}]")
    }

    // Build node info
    val nodeInfo = NodeInfo(
        nodeId = nodeId,
        hostName = hostName,
        os = System.getProperty("os.name", "unknown"),
        dockerVersion = dockerVersion,
        containers = initialContainers
    )

    // Setup network connection
    lateinit var hostConnection: HostConnection
    val clientMessageHandler = ClientMessageHandler(
        commandExecutor = commandExecutor,
        logStreamer = logStreamer,
        permissionManager = permissionManager,
        scope = this,
        onSendMessage = { message -> hostConnection.send(message) }
    )
    hostConnection = HostConnection(clientMessageHandler, useTls = useTls)

    // Connect to server
    val scheme = if (useTls) "wss" else "ws"
    logger.info("Connecting to server $scheme://$serverHost:$serverPort with host code $hostCode...")
    hostConnection.connect(serverHost, serverPort, this)

    // Wait for connection and send join request
    hostConnection.connectionState.first { it == com.orchestrator.client.network.ConnectionState.CONNECTED }
    hostConnection.send(WsMessage.JoinRequest(hostCode = hostCode, nodeInfo = nodeInfo))

    // Start container monitoring
    containerMonitor.start(this)

    // Periodically send state updates
    launch {
        while (isActive) {
            delay(5000)
            val containers = containerMonitor.containers.value
            if (containers.isNotEmpty()) {
                hostConnection.send(WsMessage.StateUpdate(
                    nodeId = nodeId,
                    containers = containers
                ))
            }
        }
    }

    logger.info("Client running. Press Ctrl+C to stop.")

    // Shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutting down...")
        containerMonitor.stop()
        hostConnection.close()
    })

    // Keep alive
    awaitCancellation()
}
