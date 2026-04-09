package com.orchestrator.common.model

import kotlinx.serialization.Serializable

@Serializable
data class DeployConfig(
    val image: String,
    val containerName: String? = null,
    val ports: List<PortMapping> = emptyList(),
    val env: List<String> = emptyList(),
    val volumes: List<String> = emptyList(),
    val restartPolicy: String = "no",
    val labels: Map<String, String> = emptyMap()
)
