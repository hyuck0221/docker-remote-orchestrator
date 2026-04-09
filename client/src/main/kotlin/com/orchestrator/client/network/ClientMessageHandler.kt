package com.orchestrator.client.network

import com.orchestrator.common.model.NodeInfo
import com.orchestrator.common.model.Permission
import com.orchestrator.common.protocol.DeployPhase
import com.orchestrator.common.protocol.WsMessage
import com.orchestrator.common.util.AppJson
import com.orchestrator.client.docker.ContainerCommandExecutor
import com.orchestrator.client.docker.ContainerDeployer
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
    private val deployer: ContainerDeployer,
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

    private val _remoteProcessingContainers = MutableStateFlow<Set<String>>(emptySet())
    val remoteProcessingContainers: StateFlow<Set<String>> = _remoteProcessingContainers.asStateFlow()

    private val _pendingDeploys = MutableStateFlow<List<WsMessage.DeployRequest>>(emptyList())
    val pendingDeploys: StateFlow<List<WsMessage.DeployRequest>> = _pendingDeploys.asStateFlow()

    private val _activeDeployNotification = MutableStateFlow<WsMessage.DeployRequest?>(null)
    val activeDeployNotification: StateFlow<WsMessage.DeployRequest?> = _activeDeployNotification.asStateFlow()

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
            is WsMessage.DeployCommand -> handleDeployCommand(message)
            is WsMessage.DeployRequest -> handleDeployRequest(message)
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
        _remoteProcessingContainers.value = state.processingContainers
    }

    private fun handleLogUnsubscribe(unsubscribe: WsMessage.LogUnsubscribe) {
        logStreamJobs.remove(unsubscribe.containerId)?.cancel()
        logger.info("Stopped log streaming for container ${unsubscribe.containerId}")
    }

    // ── Deploy Handling ──

    private fun handleDeployCommand(command: WsMessage.DeployCommand) {
        logger.info("Received instant deploy: ${command.config.image} (commandId=${command.commandId})")
        scope.launch {
            executeDeploy(command.commandId, command.config)
        }
    }

    private fun handleDeployRequest(request: WsMessage.DeployRequest) {
        logger.info("Received deploy request from ${request.fromHostName}: ${request.config.image}")
        _activeDeployNotification.value = request
    }

    fun acceptDeploy(requestId: String) {
        val request = _activeDeployNotification.value?.takeIf { it.requestId == requestId }
            ?: _pendingDeploys.value.find { it.requestId == requestId }
        if (request == null) {
            logger.warn("Deploy request not found: $requestId")
            return
        }

        _activeDeployNotification.value = null
        _pendingDeploys.value = _pendingDeploys.value.filter { it.requestId != requestId }

        scope.launch {
            onSendMessage(WsMessage.DeployResponse(requestId = requestId, nodeId = "", accepted = true))
            executeDeploy(requestId, request.config)
        }
    }

    fun deferDeploy(requestId: String) {
        val request = _activeDeployNotification.value?.takeIf { it.requestId == requestId } ?: return
        _activeDeployNotification.value = null
        _pendingDeploys.value = _pendingDeploys.value + request
        logger.info("Deploy deferred: ${request.config.image}")

        scope.launch {
            onSendMessage(WsMessage.DeployResponse(requestId = requestId, nodeId = "", accepted = false))
        }
    }

    private suspend fun executeDeploy(commandId: String, config: com.orchestrator.common.model.DeployConfig) {
        val result = deployer.deploy(commandId, config) { phase, msg ->
            onSendMessage(WsMessage.DeployProgress(
                commandId = commandId,
                nodeId = "",
                phase = phase,
                message = msg
            ))
        }
        onSendMessage(WsMessage.DeployResult(
            commandId = commandId,
            nodeId = "",
            success = result.isSuccess,
            containerId = result.getOrNull(),
            message = result.exceptionOrNull()?.message ?: if (result.isSuccess) "Deployed successfully" else ""
        ))
    }

}
