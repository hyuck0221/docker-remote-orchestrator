package com.orchestrator.server.session

import com.orchestrator.common.model.NodeInfo
import com.orchestrator.common.model.Permission
import io.ktor.websocket.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

data class NodeSession(
    val nodeId: String,
    val nodeInfo: NodeInfo,
    val wsSession: WebSocketSession,
    val permission: Permission = Permission.READ_ONLY,
    val connectedAt: Long = System.currentTimeMillis()
)

class NodeSessionManager {

    private val logger = LoggerFactory.getLogger(NodeSessionManager::class.java)
    private val sessions = ConcurrentHashMap<String, NodeSession>()

    fun addSession(nodeId: String, nodeInfo: NodeInfo, wsSession: WebSocketSession, permission: Permission): NodeSession {
        val session = NodeSession(
            nodeId = nodeId,
            nodeInfo = nodeInfo,
            wsSession = wsSession,
            permission = permission
        )
        sessions[nodeId] = session
        logger.info("Node connected: $nodeId (${nodeInfo.hostName})")
        return session
    }

    fun removeSession(nodeId: String) {
        sessions.remove(nodeId)?.let {
            logger.info("Node disconnected: $nodeId (${it.nodeInfo.hostName})")
        }
    }

    fun getSession(nodeId: String): NodeSession? = sessions[nodeId]

    fun getAllSessions(): Map<String, NodeSession> = sessions.toMap()

    fun updateNodeInfo(nodeId: String, nodeInfo: NodeInfo) {
        sessions.computeIfPresent(nodeId) { _, session ->
            session.copy(nodeInfo = nodeInfo)
        }
    }

    fun updatePermission(nodeId: String, permission: Permission): Boolean {
        val updated = sessions.computeIfPresent(nodeId) { _, session ->
            session.copy(
                permission = permission,
                nodeInfo = session.nodeInfo.copy(permission = permission)
            )
        }
        if (updated != null) {
            logger.info("Permission updated for node $nodeId: $permission")
        }
        return updated != null
    }

    fun getConnectedNodeCount(): Int = sessions.size
}
