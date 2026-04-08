package com.orchestrator.common.protocol

import com.orchestrator.common.model.ContainerInfo
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
        val action: ContainerAction
    ) : WsMessage()

    @Serializable
    @SerialName("log_subscribe")
    data class LogSubscribe(
        val containerId: String,
        val tail: Int = 100
    ) : WsMessage()

    @Serializable
    @SerialName("log_unsubscribe")
    data class LogUnsubscribe(
        val containerId: String
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
        val nodes: Map<String, NodeInfo>
    ) : WsMessage()
}

@Serializable
enum class ContainerAction {
    START,
    STOP,
    RESTART,
    REMOVE
}
