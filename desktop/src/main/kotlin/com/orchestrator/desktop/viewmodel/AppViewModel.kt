package com.orchestrator.desktop.viewmodel

import com.orchestrator.client.docker.*
import com.orchestrator.client.network.ClientMessageHandler
import com.orchestrator.client.network.ConnectionState
import com.orchestrator.client.network.HostConnection
import com.orchestrator.client.permission.PermissionManager
import com.orchestrator.common.model.ContainerInfo
import com.orchestrator.common.model.NodeInfo
import com.orchestrator.common.model.Permission
import com.orchestrator.common.protocol.WsMessage
import com.orchestrator.common.tunnel.NgrokTunnel
import com.orchestrator.common.tunnel.TunnelState
import com.orchestrator.common.util.AppState
import com.orchestrator.common.util.AppStateManager
import com.orchestrator.common.util.HostConfig
import com.orchestrator.common.util.ClientConfig
import com.orchestrator.common.util.UserSettings
import com.orchestrator.server.config.ServerConfig
import com.orchestrator.server.dashboard.DashboardStateAggregator
import com.orchestrator.server.handler.MessageHandler
import com.orchestrator.server.session.HostCodeManager
import com.orchestrator.server.session.NodeSessionManager
import com.orchestrator.server.webhook.WebhookManager
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.websocket.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import com.orchestrator.common.util.AppJson
import com.orchestrator.server.route.apiRoutes
import com.orchestrator.server.route.webSocketRoutes
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

enum class AppScreen { HOME, HOST_DASHBOARD, CLIENT_CONNECT, SETTINGS }
enum class AppRole { NONE, HOST, CLIENT }

class AppViewModel(private val scope: CoroutineScope) {

    private val logger = LoggerFactory.getLogger(AppViewModel::class.java)

    // Navigation
    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()
    private val _role = MutableStateFlow(AppRole.NONE)
    val role: StateFlow<AppRole> = _role.asStateFlow()

    // Host mode
    private val _hostCode = MutableStateFlow("")
    val hostCode: StateFlow<String> = _hostCode.asStateFlow()
    private val _connectedNodes = MutableStateFlow<Map<String, NodeInfo>>(emptyMap())
    val connectedNodes: StateFlow<Map<String, NodeInfo>> = _connectedNodes.asStateFlow()
    private val _hostLocalContainers = MutableStateFlow<List<ContainerInfo>>(emptyList())
    val hostLocalContainers: StateFlow<List<ContainerInfo>> = _hostLocalContainers.asStateFlow()
    private var serverEngine: EmbeddedServer<*, *>? = null
    private var nodeSessionManager: NodeSessionManager? = null
    private var messageHandler: MessageHandler? = null
    private var hostContainerMonitor: ContainerMonitor? = null
    private var localCommandExecutor: ContainerCommandExecutor? = null
    private var hostNodeId: String? = null

    // Client mode
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    private val _localContainers = MutableStateFlow<List<ContainerInfo>>(emptyList())
    val localContainers: StateFlow<List<ContainerInfo>> = _localContainers.asStateFlow()
    private val _clientPermission = MutableStateFlow(Permission.READ_ONLY)
    val clientPermission: StateFlow<Permission> = _clientPermission.asStateFlow()
    private val _remoteNodes = MutableStateFlow<Map<String, NodeInfo>>(emptyMap())
    val remoteNodes: StateFlow<Map<String, NodeInfo>> = _remoteNodes.asStateFlow()
    private var hostConnection: HostConnection? = null
    private var containerMonitor: ContainerMonitor? = null
    private var clientNodeId: String? = null

    // Processing state
    private val _processingContainers = MutableStateFlow<Set<String>>(emptySet())
    val processingContainers: StateFlow<Set<String>> = _processingContainers.asStateFlow()

    // Ngrok tunnel
    private var ngrokTunnel: NgrokTunnel? = null
    private val _tunnelState = MutableStateFlow(TunnelState.STOPPED)
    val tunnelState: StateFlow<TunnelState> = _tunnelState.asStateFlow()
    private val _tunnelUrl = MutableStateFlow<String?>(null)
    val tunnelUrl: StateFlow<String?> = _tunnelUrl.asStateFlow()
    private val _tunnelError = MutableStateFlow<String?>(null)
    val tunnelError: StateFlow<String?> = _tunnelError.asStateFlow()

    // Server settings
    private val _defaultPermission = MutableStateFlow(Permission.READ_ONLY)
    val defaultPermission: StateFlow<Permission> = _defaultPermission.asStateFlow()
    private val _pingInterval = MutableStateFlow(15L)
    val pingInterval: StateFlow<Long> = _pingInterval.asStateFlow()

    // Log viewer (replaces terminal)
    private val _logOutput = MutableStateFlow<List<String>>(emptyList())
    val logOutput: StateFlow<List<String>> = _logOutput.asStateFlow()
    private val _logContainerName = MutableStateFlow("")
    val logContainerName: StateFlow<String> = _logContainerName.asStateFlow()
    private var logJob: Job? = null
    private var localLogStreamer: LogStreamer? = null

    // User settings
    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    // Status
    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // ── Init: Restore saved state ──

    init {
        // Load user settings immediately
        _displayName.value = AppStateManager.loadUserSettings().displayName

        scope.launch(Dispatchers.IO) {
            delay(500) // Let UI settle
            val state = AppStateManager.load()
            when (state.role) {
                "HOST" -> state.hostConfig?.let { config ->
                    logger.info("Restoring host session: code=${config.hostCode} port=${config.port} ngrok=${config.enableNgrok}")
                    startHost(config.port, config.hostCode, enableNgrok = config.enableNgrok)
                }
                "CLIENT" -> state.clientConfig?.let { config ->
                    logger.info("Restoring client session: ${config.serverHost}:${config.serverPort}")
                    connectToHost(config.serverHost, config.serverPort, config.hostCode)
                }
            }
        }
    }

    fun navigateTo(screen: AppScreen) { _currentScreen.value = screen }

    // ── Host Mode ──

    fun startHost(port: Int = 9090, restoreCode: String? = null, enableNgrok: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            try {
                _statusMessage.value = "Starting server..."
                val config = ServerConfig(port = port)
                val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                val hostCodeMgr = HostCodeManager()
                val nodeSessionMgr = NodeSessionManager()
                val dashboardAgg = DashboardStateAggregator(nodeSessionMgr)
                val webhookMgr = WebhookManager(serverScope)
                val msgHandler = MessageHandler(hostCodeMgr, nodeSessionMgr, dashboardAgg, webhookManager = webhookMgr)

                nodeSessionManager = nodeSessionMgr
                messageHandler = msgHandler

                // Handle commands from clients targeting the host node
                msgHandler.onHostCommand = { command ->
                    executeLocalCommand(command.containerId, command.action)
                }

                val code = if (!restoreCode.isNullOrEmpty()) {
                    hostCodeMgr.restoreCode(restoreCode)
                } else {
                    hostCodeMgr.generateCode()
                }
                _hostCode.value = code

                serverEngine = embeddedServer(Netty, port = config.port, host = config.host) {
                    install(ContentNegotiation) { json(AppJson) }
                    install(WebSockets) {
                        pingPeriod = config.pingIntervalSeconds.seconds
                        timeout = config.timeoutSeconds.seconds
                        maxFrameSize = Long.MAX_VALUE
                        masking = false
                    }
                    routing {
                        webSocketRoutes(msgHandler, dashboardAgg, nodeSessionMgr)
                        apiRoutes(nodeSessionMgr, hostCodeMgr, msgHandler, webhookMgr, dashboardAgg)
                    }
                }
                serverEngine?.start(wait = false)

                // Local Docker monitoring
                var hostInfo: NodeInfo? = null
                try {
                    val dockerClient = DockerClientFactory.create()
                    val containerService = ContainerService(dockerClient)
                    localCommandExecutor = ContainerCommandExecutor(dockerClient)
                    localLogStreamer = LogStreamer(dockerClient)
                    val monitor = ContainerMonitor(dockerClient, containerService)
                    hostContainerMonitor = monitor
                    monitor.start(scope)

                    val nodeId = "host-${UUID.randomUUID().toString().take(6)}"
                    hostNodeId = nodeId
                    val systemHostName = try { InetAddress.getLocalHost().hostName } catch (_: Exception) { "Host" }
                    val hostName = if (_displayName.value.isNotBlank()) "${_displayName.value} (Host)" else "$systemHostName (Host)"

                    hostInfo = NodeInfo(
                        nodeId = nodeId,
                        hostName = hostName,
                        os = System.getProperty("os.name", "unknown"),
                        dockerVersion = containerService.getDockerVersion(),
                        containers = containerService.listContainers(),
                        permission = Permission.FULL_CONTROL
                    )
                    dashboardAgg.setHostNode(nodeId, hostInfo)

                    scope.launch {
                        monitor.containers.collect { containers ->
                            _hostLocalContainers.value = containers
                            hostInfo?.let { info ->
                                val updated = info.copy(containers = containers)
                                hostInfo = updated
                                dashboardAgg.updateHostContainers(updated)
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.warn("Could not start local Docker monitoring: ${e.message}")
                }

                // Start ngrok tunnel if enabled
                if (enableNgrok) {
                    startTunnel(port)
                }

                _role.value = AppRole.HOST
                _currentScreen.value = AppScreen.HOST_DASHBOARD
                _statusMessage.value = "Server running on port $port"

                // Save state for persistence
                AppStateManager.save(AppState(
                    role = "HOST",
                    hostConfig = HostConfig(port = port, hostCode = code, enableNgrok = enableNgrok)
                ))

                // Poll connected nodes
                while (scope.isActive) {
                    val remoteNodes = nodeSessionMgr.getAllSessions().mapValues { it.value.nodeInfo }
                    val allNodes = mutableMapOf<String, NodeInfo>()
                    hostInfo?.let { info ->
                        hostNodeId?.let { hId -> allNodes[hId] = info.copy(containers = _hostLocalContainers.value) }
                    }
                    allNodes.putAll(remoteNodes)
                    _connectedNodes.value = allNodes
                    delay(2000)
                }
            } catch (e: Exception) {
                _statusMessage.value = "Failed to start server: ${e.message}"
                logger.error("Failed to start host", e)
            }
        }
    }

    fun startTunnel(port: Int) {
        val tunnel = NgrokTunnel()
        ngrokTunnel = tunnel
        scope.launch {
            tunnel.state.collect { _tunnelState.value = it }
        }
        scope.launch {
            tunnel.publicUrl.collect { _tunnelUrl.value = it }
        }
        scope.launch {
            tunnel.errorMessage.collect { _tunnelError.value = it }
        }
        tunnel.start(port, scope)
    }

    fun stopTunnel() {
        ngrokTunnel?.stop()
        ngrokTunnel = null
        _tunnelState.value = TunnelState.STOPPED
        _tunnelUrl.value = null
        _tunnelError.value = null
    }

    fun stopHost() {
        stopTunnel()
        closeLogViewer()
        hostContainerMonitor?.stop()
        hostContainerMonitor = null
        localCommandExecutor = null
        localLogStreamer = null
        hostNodeId = null
        serverEngine?.stop(1000, 2000)
        serverEngine = null
        _role.value = AppRole.NONE
        _hostCode.value = ""
        _connectedNodes.value = emptyMap()
        _hostLocalContainers.value = emptyList()
        _currentScreen.value = AppScreen.HOME
        _statusMessage.value = "Server stopped"
        AppStateManager.clear()
    }

    fun updateNodePermission(nodeId: String, permission: Permission) {
        _connectedNodes.value = _connectedNodes.value.toMutableMap().apply {
            this[nodeId]?.let { put(nodeId, it.copy(permission = permission)) }
        }
        nodeSessionManager?.updatePermission(nodeId, permission)
        scope.launch { messageHandler?.notifyPermissionChange(nodeId, permission) }
    }

    fun sendContainerCommand(nodeId: String, containerId: String, action: com.orchestrator.common.protocol.ContainerAction) {
        if (containerId in _processingContainers.value) return
        if (nodeId == hostNodeId || nodeId == clientNodeId) { executeLocalCommand(containerId, action); return }
        _processingContainers.value = _processingContainers.value + containerId
        scope.launch {
            try {
                val command = WsMessage.ContainerCommand(
                    commandId = UUID.randomUUID().toString().take(8),
                    containerId = containerId, action = action,
                    targetNodeId = nodeId
                )
                if (_role.value == AppRole.HOST) {
                    // Host mode: send directly via server
                    val sent = messageHandler?.sendCommand(nodeId, command) ?: false
                    _statusMessage.value = if (sent) "Command sent" else "Failed to send command"
                } else {
                    // Client mode: relay through WebSocket to server
                    hostConnection?.send(command)
                    _statusMessage.value = "Command sent"
                }
                delay(3000)
            } finally { _processingContainers.value = _processingContainers.value - containerId }
        }
    }

    fun executeLocalCommand(containerId: String, action: com.orchestrator.common.protocol.ContainerAction) {
        if (containerId in _processingContainers.value) return
        _processingContainers.value = _processingContainers.value + containerId
        scope.launch(Dispatchers.IO) {
            try {
                val executor = localCommandExecutor ?: run { _statusMessage.value = "Docker not available"; return@launch }
                val result = executor.execute(containerId, action)
                _statusMessage.value = result.getOrElse { "Error: ${it.message}" }
                delay(2000)
            } finally { _processingContainers.value = _processingContainers.value - containerId }
        }
    }

    fun updateDefaultPermission(permission: Permission) { _defaultPermission.value = permission }
    fun updatePingInterval(seconds: Long) { _pingInterval.value = seconds }

    fun updateDisplayName(name: String) {
        _displayName.value = name
        scope.launch(Dispatchers.IO) {
            AppStateManager.saveUserSettings(UserSettings(displayName = name))
        }
    }

    // ── Log Viewer ──

    fun openLogViewer(containerId: String, containerName: String) {
        closeLogViewer()
        _logContainerName.value = containerName
        _logOutput.value = listOf("Streaming logs for $containerName...\n")
        val streamer = localLogStreamer ?: return
        logJob = scope.launch(Dispatchers.IO) {
            try {
                streamer.streamLogs(containerId, tail = 200).collect { line ->
                    _logOutput.value = _logOutput.value + line + "\n"
                    if (_logOutput.value.size > 2000) _logOutput.value = _logOutput.value.takeLast(1500)
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) { _logOutput.value = _logOutput.value + "Log stream error: ${e.message}\n" }
        }
    }

    fun closeLogViewer() {
        logJob?.cancel(); logJob = null
        _logOutput.value = emptyList(); _logContainerName.value = ""
    }

    // ── Client Mode ──

    fun connectToHost(serverHost: String, serverPort: Int, hostCode: String) {
        scope.launch(Dispatchers.IO) {
            try {
                _statusMessage.value = "Connecting to Docker..."
                val dockerClient = DockerClientFactory.create()
                val containerService = ContainerService(dockerClient)
                val commandExecutor = ContainerCommandExecutor(dockerClient)
                localCommandExecutor = commandExecutor
                localLogStreamer = LogStreamer(dockerClient)
                val logStreamer = LogStreamer(dockerClient)
                val permissionManager = PermissionManager()
                val monitor = ContainerMonitor(dockerClient, containerService)
                containerMonitor = monitor

                val nodeId = UUID.randomUUID().toString().take(8)
                clientNodeId = nodeId
                val systemHostName = try { InetAddress.getLocalHost().hostName } catch (_: Exception) { "unknown" }
                val clientHostName = if (_displayName.value.isNotBlank()) _displayName.value else systemHostName

                val nodeInfo = NodeInfo(
                    nodeId = nodeId, hostName = clientHostName,
                    os = System.getProperty("os.name", "unknown"),
                    dockerVersion = containerService.getDockerVersion(),
                    containers = containerService.listContainers()
                )

                lateinit var connection: HostConnection
                val clientHandler = ClientMessageHandler(
                    commandExecutor = commandExecutor, logStreamer = logStreamer,
                    permissionManager = permissionManager, scope = scope,
                    onSendMessage = { msg -> connection.send(msg) }
                )
                connection = HostConnection(clientHandler)
                hostConnection = connection

                scope.launch { connection.connectionState.collect { _connectionState.value = it } }
                scope.launch { permissionManager.currentPermission.collect { _clientPermission.value = it } }

                _statusMessage.value = "Connecting to $serverHost:$serverPort..."
                connection.connect(serverHost, serverPort, scope)

                val connected = withTimeoutOrNull(10000L) {
                    connection.connectionState.first { it == ConnectionState.CONNECTED }
                }
                if (connected == null) {
                    connection.close(); hostConnection = null; localCommandExecutor = null; clientNodeId = null
                    _statusMessage.value = "Connection failed: Cannot reach $serverHost:$serverPort"
                    _currentScreen.value = AppScreen.HOME; return@launch
                }

                connection.send(WsMessage.JoinRequest(hostCode = hostCode, nodeInfo = nodeInfo))
                val accepted = withTimeoutOrNull(5000L) { clientHandler.joinAccepted.first { it != null } }
                if (accepted != true) {
                    connection.close(); hostConnection = null; localCommandExecutor = null; clientNodeId = null
                    _statusMessage.value = "Connection rejected: Invalid host code"
                    _currentScreen.value = AppScreen.HOME; return@launch
                }

                monitor.start(scope)
                scope.launch { monitor.containers.collect { _localContainers.value = it } }
                scope.launch { clientHandler.clusterNodes.collect { nodes -> _remoteNodes.value = nodes.filterKeys { it != nodeId } } }
                scope.launch {
                    while (isActive) {
                        delay(5000)
                        val containers = monitor.containers.value
                        if (containers.isNotEmpty()) connection.send(WsMessage.StateUpdate(nodeId = nodeId, containers = containers))
                    }
                }

                _role.value = AppRole.CLIENT
                _currentScreen.value = AppScreen.CLIENT_CONNECT
                _statusMessage.value = "Connected to server"

                // Save state for persistence
                AppStateManager.save(AppState(
                    role = "CLIENT",
                    clientConfig = ClientConfig(serverHost = serverHost, serverPort = serverPort, hostCode = hostCode)
                ))
            } catch (e: Exception) {
                _statusMessage.value = "Connection failed: ${e.message}"
                logger.error("Failed to connect", e)
            }
        }
    }

    fun disconnectFromHost() {
        closeLogViewer()
        containerMonitor?.stop()
        hostConnection?.close()
        hostConnection = null; containerMonitor = null
        localCommandExecutor = null; localLogStreamer = null; clientNodeId = null
        _role.value = AppRole.NONE; _connectionState.value = ConnectionState.DISCONNECTED
        _localContainers.value = emptyList(); _remoteNodes.value = emptyMap()
        _currentScreen.value = AppScreen.HOME; _statusMessage.value = "Disconnected"
        AppStateManager.clear()
    }
}
