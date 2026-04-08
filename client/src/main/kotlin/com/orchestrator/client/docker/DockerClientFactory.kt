package com.orchestrator.client.docker

import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.api.DockerClient
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Duration

object DockerClientFactory {

    private val logger = LoggerFactory.getLogger(DockerClientFactory::class.java)

    fun create(): DockerClient {
        val dockerHost = detectDockerHost()
        logger.info("Connecting to Docker at: $dockerHost")

        val config = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(dockerHost)
            .build()

        val httpClient = ApacheDockerHttpClient.Builder()
            .dockerHost(URI.create(dockerHost))
            .maxConnections(10)
            .connectionTimeout(Duration.ofSeconds(5))
            .responseTimeout(Duration.ofSeconds(30))
            .build()

        return DockerClientImpl.getInstance(config, httpClient)
    }

    private fun detectDockerHost(): String {
        val envHost = System.getenv("DOCKER_HOST")
        if (!envHost.isNullOrBlank()) {
            logger.info("Using DOCKER_HOST from environment: $envHost")
            return envHost
        }

        val osName = System.getProperty("os.name", "").lowercase()
        return when {
            osName.contains("win") -> "npipe:////./pipe/docker_engine"
            osName.contains("mac") || osName.contains("darwin") -> {
                val homeDir = System.getProperty("user.home")
                val colimaSock = "$homeDir/.colima/default/docker.sock"
                if (java.io.File(colimaSock).exists()) {
                    "unix://$colimaSock"
                } else {
                    "unix:///var/run/docker.sock"
                }
            }
            else -> "unix:///var/run/docker.sock"
        }
    }
}
