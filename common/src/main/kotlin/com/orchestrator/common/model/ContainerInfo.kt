package com.orchestrator.common.model

import kotlinx.serialization.Serializable

@Serializable
data class ContainerInfo(
    val id: String,
    val name: String,
    val image: String,
    val status: ContainerStatus,
    val state: String,
    val ports: List<PortMapping> = emptyList(),
    val cpuUsage: Double? = null,
    val memoryUsage: Long? = null,
    val memoryLimit: Long? = null,
    val createdAt: Long = 0L
)

@Serializable
enum class ContainerStatus {
    RUNNING,
    STOPPED,
    PAUSED,
    RESTARTING,
    REMOVING,
    EXITED,
    DEAD,
    CREATED,
    UNKNOWN
}

@Serializable
data class PortMapping(
    val privatePort: Int,
    val publicPort: Int? = null,
    val type: String = "tcp",
    val ip: String? = null
)
