package com.orchestrator.server.handler

import com.orchestrator.common.model.ContainerInfo
import com.orchestrator.common.model.ContainerStatus
import com.orchestrator.common.model.Permission
import com.orchestrator.common.protocol.DeployMode
import com.orchestrator.common.protocol.WsMessage
import com.orchestrator.common.util.AppJson
import com.orchestrator.server.dashboard.DashboardStateAggregator
import com.orchestrator.server.session.HostCodeManager
import com.orchestrator.server.session.NodeSessionManager
import com.orchestrator.server.webhook.WebhookManager
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory

class MessageHandler(
    private val hostCodeManager: HostCodeManager,
    private val nodeSessionManager: NodeSessionManager,
    private val dashboardAggregator: DashboardStateAggregator,
    private val defaultPermission: Permission = Permission.READ_ONLY,
    private val webhookManager: WebhookManager? = null,
    var onHostCommand: (suspend (WsMessage.ContainerCommand) -> Unit)? = null,
    var onHostDeploy: (suspend (WsMessage.DeployCommand) -> Unit)? = null,
    var onDeployProgress: ((WsMessage.DeployProgress) -> Unit)? = null,
    var onDeployResult: ((WsMessage.DeployResult) -> Unit)? = null
) {
    private val previousContainerStates = mutableMapOf<String, Map<String, ContainerStatus>>()
    private val logger = LoggerFactory.getLogger(MessageHandler::class.java)

    /**
     * Returns the nodeId if this was a JoinRequest, null otherwise.
     */
    suspend fun handleMessage(rawMessage: String, wsSession: WebSocketSession): String? {
        val message = try {
            AppJson.decodeFromString<WsMessage>(rawMessage)
        } catch (e: Exception) {
            logger.error("Failed to parse message: $rawMessage", e)
            return null
        }

        return when (message) {
            is WsMessage.JoinRequest -> {
                handleJoinRequest(message, wsSession)
                message.nodeInfo.nodeId
            }
            is WsMessage.StateUpdate -> { handleStateUpdate(message); null }
            is WsMessage.ContainerCommand -> { handleRelayCommand(message); null }
            is WsMessage.ContainerProcessing -> { handleContainerProcessing(message); null }
            is WsMessage.LogChunk -> { handleLogChunk(message, wsSession); null }
            is WsMessage.CommandResult -> { handleCommandResult(message); null }
            is WsMessage.DeployCommand -> { handleDeployCommand(message); null }
            is WsMessage.DeployResponse -> { logger.info("Deploy response: ${message.requestId} accepted=${message.accepted}"); null }
            is WsMessage.DeployProgress -> { handleDeployProgress(message); null }
            is WsMessage.DeployResult -> { handleDeployResult(message); null }
            else -> { logger.warn("Unexpected message type from client: ${message::class.simpleName}"); null }
        }
    }

    fun removeNode(nodeId: String) {
        nodeSessionManager.removeSession(nodeId)
    }

    suspend fun notifyPermissionChange(nodeId: String, permission: Permission) {
        val session = nodeSessionManager.getSession(nodeId) ?: return
        try {
            val msg = WsMessage.PermissionUpdate(permission = permission)
            session.wsSession.send(Frame.Text(AppJson.encodeToString<WsMessage>(msg)))
            logger.info("Sent permission update to node $nodeId: $permission")
        } catch (e: Exception) {
            logger.error("Failed to send permission update to node $nodeId", e)
        }
    }

    private suspend fun handleJoinRequest(request: WsMessage.JoinRequest, wsSession: WebSocketSession) {
        if (!hostCodeManager.validateCode(request.hostCode)) {
            val response = WsMessage.JoinResponse(
                accepted = false,
                reason = "Invalid host code"
            )
            wsSession.send(Frame.Text(AppJson.encodeToString<WsMessage>(response)))
            return
        }

        nodeSessionManager.addSession(
            nodeId = request.nodeInfo.nodeId,
            nodeInfo = request.nodeInfo,
            wsSession = wsSession,
            permission = defaultPermission
        )

        val response = WsMessage.JoinResponse(
            accepted = true,
            assignedPermission = defaultPermission
        )
        wsSession.send(Frame.Text(AppJson.encodeToString<WsMessage>(response)))

        dashboardAggregator.broadcastClusterState()
        logger.info("Node joined: ${request.nodeInfo.nodeId} (${request.nodeInfo.hostName})")
    }

    private suspend fun handleStateUpdate(update: WsMessage.StateUpdate) {
        val session = nodeSessionManager.getSession(update.nodeId) ?: return

        // Detect container state changes for webhooks
        detectStateChanges(update.nodeId, session.nodeInfo.containers, update.containers)

        val updatedInfo = session.nodeInfo.copy(containers = update.containers)
        nodeSessionManager.updateNodeInfo(update.nodeId, updatedInfo)
        dashboardAggregator.broadcastClusterState()
    }

    private fun detectStateChanges(nodeId: String, old: List<ContainerInfo>, new: List<ContainerInfo>) {
        if (webhookManager == null) return
        val oldMap = old.associateBy { it.id }
        for (container in new) {
            val prev = oldMap[container.id] ?: continue
            if (prev.status == ContainerStatus.RUNNING && container.status != ContainerStatus.RUNNING) {
                webhookManager.notify(
                    event = "container_stop",
                    nodeId = nodeId,
                    containerId = container.id,
                    containerName = container.name,
                    detail = "Status changed: ${prev.status} -> ${container.status}"
                )
            }
            if (prev.status != ContainerStatus.RUNNING && container.status == ContainerStatus.RUNNING) {
                webhookManager.notify(
                    event = "container_start",
                    nodeId = nodeId,
                    containerId = container.id,
                    containerName = container.name,
                    detail = "Status changed: ${prev.status} -> ${container.status}"
                )
            }
        }
    }

    // Tracks log callbacks: containerId -> callback that receives log lines
    private val logCallbacks = mutableMapOf<String, (WsMessage.LogChunk) -> Unit>()
    // Tracks WebSocket-based log subscribers: containerId -> session
    private val logSubscribers = mutableMapOf<String, WebSocketSession>()

    private suspend fun handleLogChunk(logChunk: WsMessage.LogChunk, senderSession: WebSocketSession) {
        logger.debug("Log chunk from ${logChunk.nodeId} for container ${logChunk.containerId}: ${logChunk.lines.size} lines")
        // Forward to callback subscriber (host desktop app)
        logCallbacks[logChunk.containerId]?.invoke(logChunk)
        // Forward to WebSocket subscriber (dashboard)
        val subscriber = logSubscribers[logChunk.containerId]
        if (subscriber != null) {
            try {
                subscriber.send(Frame.Text(AppJson.encodeToString<WsMessage>(logChunk)))
            } catch (e: Exception) {
                logger.warn("Failed to forward log chunk for container ${logChunk.containerId}", e)
                logSubscribers.remove(logChunk.containerId)
            }
        }
    }

    suspend fun subscribeRemoteLogs(targetNodeId: String, containerId: String, tail: Int = 200, onLogChunk: (WsMessage.LogChunk) -> Unit) {
        val session = nodeSessionManager.getSession(targetNodeId) ?: run {
            logger.warn("Log subscribe: node not found: $targetNodeId")
            return
        }
        logCallbacks[containerId] = onLogChunk
        try {
            val msg = WsMessage.LogSubscribe(containerId = containerId, tail = tail, targetNodeId = targetNodeId)
            session.wsSession.send(Frame.Text(AppJson.encodeToString<WsMessage>(msg)))
            logger.info("Subscribed to remote logs: node=$targetNodeId container=$containerId")
        } catch (e: Exception) {
            logger.error("Failed to subscribe to remote logs on node $targetNodeId", e)
            logCallbacks.remove(containerId)
        }
    }

    suspend fun unsubscribeRemoteLogs(targetNodeId: String, containerId: String) {
        logCallbacks.remove(containerId)
        val session = nodeSessionManager.getSession(targetNodeId) ?: return
        try {
            val msg = WsMessage.LogUnsubscribe(containerId = containerId, targetNodeId = targetNodeId)
            session.wsSession.send(Frame.Text(AppJson.encodeToString<WsMessage>(msg)))
            logger.info("Unsubscribed from remote logs: node=$targetNodeId container=$containerId")
        } catch (e: Exception) {
            logger.error("Failed to unsubscribe from remote logs on node $targetNodeId", e)
        }
    }

    private suspend fun handleRelayCommand(command: WsMessage.ContainerCommand) {
        val targetId = command.targetNodeId
        if (targetId == null) {
            logger.warn("Relay command missing targetNodeId")
            return
        }

        // If targeting the host node, execute locally
        if (targetId.startsWith("host-")) {
            logger.info("Executing host command: ${command.action} on container ${command.containerId}")
            onHostCommand?.invoke(command)
            return
        }

        logger.info("Relaying command ${command.action} to node $targetId for container ${command.containerId}")
        sendCommand(targetId, command)
    }

    private suspend fun handleContainerProcessing(msg: WsMessage.ContainerProcessing) {
        dashboardAggregator.setContainerProcessing(msg.containerId, msg.processing)
        dashboardAggregator.broadcastClusterState()
    }

    private fun handleCommandResult(result: WsMessage.CommandResult) {
        logger.info("Command ${result.commandId} on node ${result.nodeId}: success=${result.success} ${result.message}")
    }

    suspend fun sendCommand(nodeId: String, command: WsMessage.ContainerCommand): Boolean {
        val session = nodeSessionManager.getSession(nodeId) ?: run {
            logger.warn("Node not found: $nodeId")
            return false
        }

        // Host is the authority - always allow sending commands to nodes
        try {
            session.wsSession.send(Frame.Text(AppJson.encodeToString<WsMessage>(command)))
            logger.info("Command ${command.action} sent to node $nodeId for container ${command.containerId}")
            return true
        } catch (e: Exception) {
            logger.error("Failed to send command to node $nodeId", e)
            return false
        }
    }

    // ── Deploy Routing ──

    suspend fun sendDeployCommand(command: WsMessage.DeployCommand): Boolean {
        val session = nodeSessionManager.getSession(command.targetNodeId) ?: run {
            logger.warn("Deploy target node not found: ${command.targetNodeId}")
            return false
        }

        try {
            when (command.deployMode) {
                DeployMode.INSTANT -> {
                    session.wsSession.send(Frame.Text(AppJson.encodeToString<WsMessage>(command)))
                    logger.info("Instant deploy sent to node ${command.targetNodeId}: ${command.config.image}")
                }
                DeployMode.APPROVAL -> {
                    val hostInfo = dashboardAggregator.getHostNode()
                    val request = WsMessage.DeployRequest(
                        requestId = command.commandId,
                        fromHostName = hostInfo?.second?.hostName ?: "Host",
                        config = command.config
                    )
                    session.wsSession.send(Frame.Text(AppJson.encodeToString<WsMessage>(request)))
                    logger.info("Deploy request sent to node ${command.targetNodeId}: ${command.config.image}")
                }
            }
            return true
        } catch (e: Exception) {
            logger.error("Failed to send deploy to node ${command.targetNodeId}", e)
            return false
        }
    }

    private suspend fun handleDeployCommand(command: WsMessage.DeployCommand) {
        val targetId = command.targetNodeId
        if (targetId.startsWith("host-")) {
            logger.info("Routing deploy to host node: ${command.config.image}")
            onHostDeploy?.invoke(command)
            return
        }
        logger.info("Routing deploy command to node $targetId: ${command.config.image}")
        sendDeployCommand(command)
    }

    private fun handleDeployProgress(progress: WsMessage.DeployProgress) {
        logger.info("Deploy progress [${progress.commandId}]: ${progress.phase} - ${progress.message}")
        onDeployProgress?.invoke(progress)
    }

    private fun handleDeployResult(result: WsMessage.DeployResult) {
        logger.info("Deploy result [${result.commandId}]: success=${result.success} ${result.message}")
        onDeployResult?.invoke(result)
        if (webhookManager != null) {
            webhookManager.notify(
                event = if (result.success) "container_deploy_success" else "container_deploy_failed",
                nodeId = result.nodeId,
                containerId = result.containerId ?: "",
                containerName = "",
                detail = result.message
            )
        }
    }
}
