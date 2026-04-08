package com.orchestrator.server.route

import com.orchestrator.server.dashboard.DashboardStateAggregator
import com.orchestrator.server.handler.MessageHandler
import com.orchestrator.server.session.NodeSessionManager
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("WebSocketRoutes")

fun Route.webSocketRoutes(
    messageHandler: MessageHandler,
    dashboardAggregator: DashboardStateAggregator,
    nodeSessionManager: NodeSessionManager? = null
) {
    webSocket("/ws/node") {
        logger.info("New node WebSocket connection")
        var trackedNodeId: String? = null
        try {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        val nodeId = messageHandler.handleMessage(text, this)
                        if (nodeId != null && trackedNodeId == null) {
                            trackedNodeId = nodeId
                        }
                    }
                    is Frame.Close -> {
                        logger.info("Node connection closed: $trackedNodeId")
                        break
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            logger.error("Node WebSocket error (node=$trackedNodeId)", e)
        } finally {
            trackedNodeId?.let { nodeId ->
                nodeSessionManager?.removeSession(nodeId)
                    ?: messageHandler.removeNode(nodeId)
                logger.info("Node session cleaned up: $nodeId")
            }
        }
    }

    webSocket("/ws/dashboard") {
        val dashboardId = UUID.randomUUID().toString().take(8)
        dashboardAggregator.addDashboardSession(dashboardId, this)
        try {
            dashboardAggregator.broadcastClusterState()
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        logger.debug("Dashboard message: ${frame.readText()}")
                    }
                    is Frame.Close -> break
                    else -> {}
                }
            }
        } catch (e: Exception) {
            logger.error("Dashboard WebSocket error", e)
        } finally {
            dashboardAggregator.removeDashboardSession(dashboardId)
        }
    }
}
