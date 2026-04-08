package com.orchestrator.client.network

import com.orchestrator.common.model.NodeInfo
import com.orchestrator.common.model.Permission
import com.orchestrator.common.protocol.WsMessage
import com.orchestrator.common.util.AppJson
import com.orchestrator.client.docker.ContainerCommandExecutor
import com.orchestrator.client.docker.LogStreamer
import com.orchestrator.client.permission.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class ClientMessageHandler(
    private val commandExecutor: ContainerCommandExecutor,
    private val logStreamer: LogStreamer,
    private val permissionManager: PermissionManager,
    private val scope: CoroutineScope,
    private val onSendMessage: suspend (WsMessage) -> Unit
) {
    private val logger = LoggerFactory.getLogger(ClientMessageHandler::class.java)

    private val _joinAccepted = MutableStateFlow<Boolean?>(null)
    val joinAccepted: StateFlow<Boolean?> = _joinAccepted.asStateFlow()

    private val _clusterNodes = MutableStateFlow<Map<String, NodeInfo>>(emptyMap())
    val clusterNodes: StateFlow<Map<String, NodeInfo>> = _clusterNodes.asStateFlow()

    private val logStreamJobs = mutableMapOf<String, Job>()

    fun handleMessage(rawMessage: String) {
        val message = try {
            AppJson.decodeFromString<WsMessage>(rawMessage)
        } catch (e: Exception) {
            logger.error("Failed to parse server message: $rawMessage", e)
            return
        }

        when (message) {
            is WsMessage.JoinResponse -> handleJoinResponse(message)
            is WsMessage.ContainerCommand -> handleContainerCommand(message)
            is WsMessage.LogSubscribe -> handleLogSubscribe(message)
            is WsMessage.LogUnsubscribe -> handleLogUnsubscribe(message)
            is WsMessage.PermissionUpdate -> handlePermissionUpdate(message)
            is WsMessage.ClusterState -> handleClusterState(message)
            else -> logger.warn("Unexpected message type from server: ${message::class.simpleName}")
        }
    }

    private fun handleJoinResponse(response: WsMessage.JoinResponse) {
        _joinAccepted.value = response.accepted
        if (response.accepted) {
            permissionManager.updatePermission(response.assignedPermission)
            logger.info("Joined server successfully (permission: ${response.assignedPermission})")
        } else {
            logger.warn("Join rejected: ${response.reason}")
        }
    }

    private fun handleContainerCommand(command: WsMessage.ContainerCommand) {
        // Commands from the host server are always honored - the host is the authority
        logger.info("Executing command: ${command.action} on container ${command.containerId}")
        scope.launch {
            val result = commandExecutor.execute(command.containerId, command.action)
            onSendMessage(WsMessage.CommandResult(
                nodeId = "",
                commandId = command.commandId,
                success = result.isSuccess,
                message = result.getOrElse { it.message ?: "Unknown error" }
            ))
        }
    }

    private fun handleLogSubscribe(subscribe: WsMessage.LogSubscribe) {
        val containerId = subscribe.containerId
        logStreamJobs[containerId]?.cancel()

        logStreamJobs[containerId] = scope.launch {
            logStreamer.streamLogs(containerId, subscribe.tail).collect { line ->
                onSendMessage(WsMessage.LogChunk(
                    nodeId = "",
                    containerId = containerId,
                    lines = listOf(line)
                ))
            }
        }
        logger.info("Started log streaming for container $containerId")
    }

    private fun handlePermissionUpdate(update: WsMessage.PermissionUpdate) {
        permissionManager.updatePermission(update.permission)
        logger.info("Permission updated by server: ${update.permission}")
    }

    private fun handleClusterState(state: WsMessage.ClusterState) {
        _clusterNodes.value = state.nodes
    }

    private fun handleLogUnsubscribe(unsubscribe: WsMessage.LogUnsubscribe) {
        logStreamJobs.remove(unsubscribe.containerId)?.cancel()
        logger.info("Stopped log streaming for container ${unsubscribe.containerId}")
    }

}
