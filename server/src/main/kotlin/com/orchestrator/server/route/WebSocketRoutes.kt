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
                        // Isolate per-message exceptions so one bad payload
                        // doesn't kill the whole session (which would trigger
                        // the client reconnect loop and make nodes "flicker").
                        try {
                            val nodeId = messageHandler.handleMessage(text, this)
                            if (nodeId != null && trackedNodeId == null) {
                                trackedNodeId = nodeId
                            }
                        } catch (e: Exception) {
                            logger.error("handleMessage failed for node=$trackedNodeId payload=${text.take(200)}", e)
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
                if (nodeSessionManager != null) {
                    nodeSessionManager.removeSessionIfMatch(nodeId, this)
                } else {
                    messageHandler.removeNode(nodeId)
                }
                logger.info("Node session cleanup attempted: $nodeId")
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
