package com.orchestrator.server.handler

import com.orchestrator.common.model.Permission
import com.orchestrator.common.protocol.WsMessage
import com.orchestrator.common.util.AppJson
import com.orchestrator.common.model.ContainerInfo
import com.orchestrator.common.model.ContainerStatus
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
    private val webhookManager: WebhookManager? = null
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
            is WsMessage.LogChunk -> { handleLogChunk(message); null }
            is WsMessage.CommandResult -> { handleCommandResult(message); null }
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

    private fun handleLogChunk(logChunk: WsMessage.LogChunk) {
        logger.debug("Log chunk from ${logChunk.nodeId} for container ${logChunk.containerId}: ${logChunk.lines.size} lines")
        // TODO: Forward to dashboard subscribers
    }

    private fun handleCommandResult(result: WsMessage.CommandResult) {
        logger.info("Command ${result.commandId} on node ${result.nodeId}: success=${result.success} ${result.message}")
    }

    suspend fun sendCommand(nodeId: String, command: WsMessage.ContainerCommand): Boolean {
        val session = nodeSessionManager.getSession(nodeId) ?: run {
            logger.warn("Node not found: $nodeId")
            return false
        }

        if (session.permission == Permission.READ_ONLY || session.permission == Permission.DENIED) {
            logger.warn("Permission denied for command on node $nodeId (permission=${session.permission})")
            return false
        }

        try {
            session.wsSession.send(Frame.Text(AppJson.encodeToString<WsMessage>(command)))
            return true
        } catch (e: Exception) {
            logger.error("Failed to send command to node $nodeId", e)
            return false
        }
    }
}
