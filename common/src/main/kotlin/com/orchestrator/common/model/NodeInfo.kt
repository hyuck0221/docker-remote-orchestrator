package com.orchestrator.common.model

import kotlinx.serialization.Serializable

@Serializable
data class NodeInfo(
    val nodeId: String,
    val hostName: String,
    val os: String,
    val dockerVersion: String,
    val containers: List<ContainerInfo> = emptyList(),
    val permission: Permission = Permission.READ_ONLY,
    val connectedAt: Long = System.currentTimeMillis()
)
