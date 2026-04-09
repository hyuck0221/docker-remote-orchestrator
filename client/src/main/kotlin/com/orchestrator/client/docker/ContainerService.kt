package com.orchestrator.client.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Container
import com.orchestrator.common.model.ContainerInfo
import com.orchestrator.common.model.ContainerStatus
import com.orchestrator.common.model.DeployConfig
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

    fun inspectContainerConfig(containerId: String): DeployConfig? {
        return try {
            val inspection = dockerClient.inspectContainerCmd(containerId).exec()
            val config = inspection.config
            val hostConfig = inspection.hostConfig

            val env = config?.env?.toList() ?: emptyList()
            val labels = config?.labels ?: emptyMap()

            // Extract volume binds
            val volumes = hostConfig?.binds?.map { it.toString() } ?: emptyList()

            // Extract port mappings from host config
            val ports = hostConfig?.portBindings?.bindings?.flatMap { (exposedPort, bindings) ->
                bindings?.map { binding ->
                    PortMapping(
                        privatePort = exposedPort.port,
                        publicPort = binding.hostPortSpec?.toIntOrNull(),
                        type = exposedPort.protocol?.name?.lowercase() ?: "tcp"
                    )
                } ?: emptyList()
            } ?: emptyList()

            // Restart policy
            val restartPolicy = when (hostConfig?.restartPolicy?.name) {
                "always" -> "always"
                "unless-stopped" -> "unless-stopped"
                "on-failure" -> "on-failure"
                else -> "no"
            }

            DeployConfig(
                image = config?.image ?: inspection.imageId ?: "",
                containerName = inspection.name?.removePrefix("/"),
                ports = ports,
                env = env,
                volumes = volumes,
                restartPolicy = restartPolicy,
                labels = labels
            )
        } catch (e: Exception) {
            logger.error("Failed to inspect container $containerId", e)
            null
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
        val labels = this.labels ?: emptyMap()
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
            createdAt = this.created ?: 0L,
            uptime = this.status ?: "",
            composeProject = labels["com.docker.compose.project"],
            composeService = labels["com.docker.compose.service"]
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
