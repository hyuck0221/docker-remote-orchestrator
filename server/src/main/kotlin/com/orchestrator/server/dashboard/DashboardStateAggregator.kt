package com.orchestrator.server.dashboard

import com.orchestrator.common.model.NodeInfo
import com.orchestrator.common.model.Permission
import com.orchestrator.common.protocol.WsMessage
import com.orchestrator.common.util.AppJson
import com.orchestrator.server.session.NodeSessionManager
import io.ktor.websocket.*
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class DashboardStateAggregator(
    private val nodeSessionManager: NodeSessionManager
) {
    private val logger = LoggerFactory.getLogger(DashboardStateAggregator::class.java)
    private val dashboardSessions = ConcurrentHashMap<String, WebSocketSession>()
    private val hostNode = AtomicReference<Pair<String, NodeInfo>?>(null)
    private val _processingContainers = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun setContainerProcessing(containerId: String, processing: Boolean) {
        if (processing) _processingContainers.add(containerId) else _processingContainers.remove(containerId)
    }

    fun getProcessingContainers(): Set<String> = _processingContainers.toSet()

    fun setHostNode(nodeId: String, nodeInfo: NodeInfo) {
        hostNode.set(nodeId to nodeInfo)
    }

    fun updateHostContainers(nodeInfo: NodeInfo) {
        hostNode.get()?.let { (id, _) ->
            hostNode.set(id to nodeInfo)
        }
    }

    fun getHostNode(): Pair<String, NodeInfo>? = hostNode.get()

    fun addDashboardSession(id: String, session: WebSocketSession) {
        dashboardSessions[id] = session
        logger.info("Dashboard connected: $id")
    }

    fun removeDashboardSession(id: String) {
        dashboardSessions.remove(id)
        logger.info("Dashboard disconnected: $id")
    }

    suspend fun broadcastClusterState() {
        val allSessions = nodeSessionManager.getAllSessions()
        val nodes = mutableMapOf<String, NodeInfo>()

        // Include host node
        hostNode.get()?.let { (id, info) -> nodes[id] = info }

        // Include remote nodes
        nodes.putAll(allSessions.mapValues { it.value.nodeInfo })

        val clusterState = WsMessage.ClusterState(nodes = nodes, processingContainers = getProcessingContainers())
        val json = AppJson.encodeToString<WsMessage>(clusterState)

        // Broadcast to dashboard sessions
        dashboardSessions.values.forEach { session ->
            try {
                session.send(Frame.Text(json))
            } catch (e: Exception) {
                logger.warn("Failed to broadcast to dashboard session", e)
            }
        }

        // Broadcast to node clients (filtered by permission)
        allSessions.forEach { (nodeId, nodeSession) ->
            if (nodeSession.permission == Permission.DENIED) return@forEach

            try {
                nodeSession.wsSession.send(Frame.Text(json))
            } catch (e: Exception) {
                logger.warn("Failed to send cluster state to node $nodeId", e)
            }
        }
    }
}
