package com.orchestrator.common.protocol

import com.orchestrator.common.model.ContainerInfo
import com.orchestrator.common.model.DeployConfig
import com.orchestrator.common.model.NodeInfo
import com.orchestrator.common.model.Permission
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class WsMessage {

    // ── Client → Server ──

    @Serializable
    @SerialName("join_request")
    data class JoinRequest(
        val hostCode: String,
        val nodeInfo: NodeInfo
    ) : WsMessage()

    @Serializable
    @SerialName("state_update")
    data class StateUpdate(
        val nodeId: String,
        val containers: List<ContainerInfo>
    ) : WsMessage()

    @Serializable
    @SerialName("log_chunk")
    data class LogChunk(
        val nodeId: String,
        val containerId: String,
        val lines: List<String>
    ) : WsMessage()

    @Serializable
    @SerialName("command_result")
    data class CommandResult(
        val nodeId: String,
        val commandId: String,
        val success: Boolean,
        val message: String = ""
    ) : WsMessage()

    // ── Server → Client ──

    @Serializable
    @SerialName("join_response")
    data class JoinResponse(
        val accepted: Boolean,
        val reason: String? = null,
        val assignedPermission: Permission = Permission.READ_ONLY
    ) : WsMessage()

    @Serializable
    @SerialName("container_command")
    data class ContainerCommand(
        val commandId: String,
        val containerId: String,
        val action: ContainerAction,
        val targetNodeId: String? = null
    ) : WsMessage()

    @Serializable
    @SerialName("log_subscribe")
    data class LogSubscribe(
        val containerId: String,
        val tail: Int = 100,
        val targetNodeId: String? = null
    ) : WsMessage()

    @Serializable
    @SerialName("log_unsubscribe")
    data class LogUnsubscribe(
        val containerId: String,
        val targetNodeId: String? = null
    ) : WsMessage()

    @Serializable
    @SerialName("permission_update")
    data class PermissionUpdate(
        val permission: Permission
    ) : WsMessage()

    // ── Server → Dashboard ──

    @Serializable
    @SerialName("cluster_state")
    data class ClusterState(
        val nodes: Map<String, NodeInfo>,
        val processingContainers: Set<String> = emptySet()
    ) : WsMessage()

    // ── Bidirectional ──

    @Serializable
    @SerialName("container_processing")
    data class ContainerProcessing(
        val containerId: String,
        val processing: Boolean
    ) : WsMessage()

    // ── Deploy Messages ──

    @Serializable
    @SerialName("deploy_command")
    data class DeployCommand(
        val commandId: String,
        val targetNodeId: String,
        val config: DeployConfig,
        val deployMode: DeployMode
    ) : WsMessage()

    @Serializable
    @SerialName("deploy_request")
    data class DeployRequest(
        val requestId: String,
        val fromHostName: String,
        val config: DeployConfig,
        val requestedAt: Long = System.currentTimeMillis()
    ) : WsMessage()

    @Serializable
    @SerialName("deploy_response")
    data class DeployResponse(
        val requestId: String,
        val nodeId: String,
        val accepted: Boolean
    ) : WsMessage()

    @Serializable
    @SerialName("deploy_progress")
    data class DeployProgress(
        val commandId: String,
        val nodeId: String,
        val phase: DeployPhase,
        val message: String = ""
    ) : WsMessage()

    @Serializable
    @SerialName("deploy_result")
    data class DeployResult(
        val commandId: String,
        val nodeId: String,
        val success: Boolean,
        val containerId: String? = null,
        val message: String = ""
    ) : WsMessage()
}

@Serializable
enum class ContainerAction {
    START,
    STOP,
    RESTART,
    REMOVE
}

@Serializable
enum class DeployMode {
    INSTANT,
    APPROVAL
}

@Serializable
enum class DeployPhase {
    PULLING,
    CREATING,
    STARTING,
    COMPLETE,
    FAILED
}
