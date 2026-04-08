package com.orchestrator.client.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Container
import com.orchestrator.common.model.ContainerInfo
import com.orchestrator.common.model.ContainerStatus
import com.orchestrator.common.model.PortMapping
import org.slf4j.LoggerFactory

class ContainerService(private val dockerClient: DockerClient) {

    private val logger = LoggerFactory.getLogger(ContainerService::class.java)

    fun listContainers(all: Boolean = true): List<ContainerInfo> {
        return try {
            dockerClient.listContainersCmd()
                .withShowAll(all)
                .exec()
                .map { it.toContainerInfo() }
        } catch (e: Exception) {
            logger.error("Failed to list containers", e)
            emptyList()
        }
    }

    fun getDockerVersion(): String {
        return try {
            dockerClient.versionCmd().exec().version ?: "unknown"
        } catch (e: Exception) {
            logger.error("Failed to get Docker version", e)
            "unknown"
        }
    }

    private fun Container.toContainerInfo(): ContainerInfo {
        return ContainerInfo(
            id = this.id.take(12),
            name = this.names?.firstOrNull()?.removePrefix("/") ?: "unnamed",
            image = this.image ?: "unknown",
            status = mapStatus(this.state ?: ""),
            state = this.state ?: "unknown",
            ports = this.ports?.map { port ->
                PortMapping(
                    privatePort = port.privatePort ?: 0,
                    publicPort = port.publicPort,
                    type = port.type?.toString()?.lowercase() ?: "tcp",
                    ip = port.ip
                )
            } ?: emptyList(),
            createdAt = this.created ?: 0L
        )
    }

    private fun mapStatus(state: String): ContainerStatus {
        return when (state.lowercase()) {
            "running" -> ContainerStatus.RUNNING
            "exited" -> ContainerStatus.EXITED
            "paused" -> ContainerStatus.PAUSED
            "restarting" -> ContainerStatus.RESTARTING
            "removing" -> ContainerStatus.REMOVING
            "dead" -> ContainerStatus.DEAD
            "created" -> ContainerStatus.CREATED
            else -> ContainerStatus.UNKNOWN
        }
    }
}
