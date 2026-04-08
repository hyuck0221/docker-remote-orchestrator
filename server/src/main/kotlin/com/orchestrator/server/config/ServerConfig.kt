package com.orchestrator.server.config

data class ServerConfig(
    val port: Int = 8080,
    val sslPort: Int = 8443,
    val host: String = "0.0.0.0",
    val pingIntervalSeconds: Long = 15,
    val timeoutSeconds: Long = 30,
    val enableTls: Boolean = false
)
