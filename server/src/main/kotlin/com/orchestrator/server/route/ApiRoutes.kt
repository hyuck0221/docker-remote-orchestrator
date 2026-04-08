package com.orchestrator.server.route

import com.orchestrator.common.model.Permission
import com.orchestrator.common.protocol.ContainerAction
import com.orchestrator.common.protocol.WsMessage
import com.orchestrator.server.handler.MessageHandler
import com.orchestrator.server.dashboard.DashboardStateAggregator
import com.orchestrator.server.session.HostCodeManager
import com.orchestrator.server.session.NodeSessionManager
import com.orchestrator.server.webhook.WebhookManager
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CommandRequest(
    val containerId: String,
    val action: ContainerAction
)

@Serializable
data class PermissionRequest(
    val permission: Permission
)

@Serializable
data class WebhookRequest(
    val url: String,
    val events: List<String> = listOf("container_stop")
)

@Serializable
data class ApiResponse(
    val success: Boolean,
    val message: String,
    val data: kotlinx.serialization.json.JsonElement? = null
)

fun Route.apiRoutes(
    nodeSessionManager: NodeSessionManager,
    hostCodeManager: HostCodeManager,
    messageHandler: MessageHandler,
    webhookManager: WebhookManager,
    dashboardAggregator: DashboardStateAggregator? = null
) {
    route("/api") {

        // ── Cluster Info ──

        get("/cluster") {
            val allNodes = mutableListOf<Map<String, String>>()

            // Include host node
            dashboardAggregator?.getHostNode()?.let { (id, info) ->
                allNodes.add(mapOf(
                    "nodeId" to id,
                    "hostName" to info.hostName,
                    "os" to info.os,
                    "dockerVersion" to info.dockerVersion,
                    "permission" to info.permission.name,
                    "containerCount" to info.containers.size.toString(),
                    "connectedAt" to info.connectedAt.toString()
                ))
            }

            // Include remote nodes
            nodeSessionManager.getAllSessions().forEach { (id, session) ->
                allNodes.add(mapOf(
                    "nodeId" to id,
                    "hostName" to session.nodeInfo.hostName,
                    "os" to session.nodeInfo.os,
                    "dockerVersion" to session.nodeInfo.dockerVersion,
                    "permission" to session.permission.name,
                    "containerCount" to session.nodeInfo.containers.size.toString(),
                    "connectedAt" to session.connectedAt.toString()
                ))
            }
            call.respond(allNodes)
        }

        get("/host-code") {
            val code = hostCodeManager.getActiveCode()
            call.respond(mapOf("hostCode" to (code ?: ""), "active" to (code != null).toString()))
        }

        post("/host-code/regenerate") {
            val newCode = hostCodeManager.generateCode()
            call.respond(mapOf("hostCode" to newCode))
        }

        // ── Node Details ──

        get("/nodes/{nodeId}") {
            val nodeId = call.parameters["nodeId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ApiResponse(false, "Missing nodeId")
            )
            val session = nodeSessionManager.getSession(nodeId) ?: return@get call.respond(
                HttpStatusCode.NotFound, ApiResponse(false, "Node not found")
            )
            call.respond(session.nodeInfo)
        }

        get("/nodes/{nodeId}/containers") {
            val nodeId = call.parameters["nodeId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ApiResponse(false, "Missing nodeId")
            )
            val session = nodeSessionManager.getSession(nodeId) ?: return@get call.respond(
                HttpStatusCode.NotFound, ApiResponse(false, "Node not found")
            )
            call.respond(session.nodeInfo.containers)
        }

        // ── Remote Control ──

        post("/nodes/{nodeId}/command") {
            val nodeId = call.parameters["nodeId"] ?: return@post call.respond(
                HttpStatusCode.BadRequest, ApiResponse(false, "Missing nodeId")
            )
            val request = call.receive<CommandRequest>()
            val command = WsMessage.ContainerCommand(
                commandId = UUID.randomUUID().toString().take(8),
                containerId = request.containerId,
                action = request.action
            )
            val sent = messageHandler.sendCommand(nodeId, command)
            if (sent) {
                call.respond(ApiResponse(true, "Command sent", null))
            } else {
                call.respond(HttpStatusCode.Forbidden, ApiResponse(false, "Permission denied or node not found"))
            }
        }

        // ── Permission Management ──

        get("/nodes/{nodeId}/permission") {
            val nodeId = call.parameters["nodeId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest, ApiResponse(false, "Missing nodeId")
            )
            val session = nodeSessionManager.getSession(nodeId) ?: return@get call.respond(
                HttpStatusCode.NotFound, ApiResponse(false, "Node not found")
            )
            call.respond(mapOf("nodeId" to nodeId, "permission" to session.permission.name))
        }

        put("/nodes/{nodeId}/permission") {
            val nodeId = call.parameters["nodeId"] ?: return@put call.respond(
                HttpStatusCode.BadRequest, ApiResponse(false, "Missing nodeId")
            )
            val request = call.receive<PermissionRequest>()
            val updated = nodeSessionManager.updatePermission(nodeId, request.permission)
            if (updated) {
                messageHandler.notifyPermissionChange(nodeId, request.permission)
                call.respond(ApiResponse(true, "Permission updated to ${request.permission}"))
            } else {
                call.respond(HttpStatusCode.NotFound, ApiResponse(false, "Node not found"))
            }
        }

        // ── Webhook Management ──

        get("/webhooks") {
            call.respond(webhookManager.listWebhooks())
        }

        post("/webhooks") {
            val request = call.receive<WebhookRequest>()
            val id = webhookManager.addWebhook(request.url, request.events)
            call.respond(HttpStatusCode.Created, mapOf("id" to id, "url" to request.url))
        }

        delete("/webhooks/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(
                HttpStatusCode.BadRequest, ApiResponse(false, "Missing id")
            )
            webhookManager.removeWebhook(id)
            call.respond(ApiResponse(true, "Webhook removed"))
        }
    }
}
