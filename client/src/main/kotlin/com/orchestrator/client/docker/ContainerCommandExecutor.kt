package com.orchestrator.client.docker

import com.github.dockerjava.api.DockerClient
import com.orchestrator.common.protocol.ContainerAction
import org.slf4j.LoggerFactory

class ContainerCommandExecutor(private val dockerClient: DockerClient) {

    private val logger = LoggerFactory.getLogger(ContainerCommandExecutor::class.java)

    fun execute(containerId: String, action: ContainerAction): Result<String> {
        return try {
            when (action) {
                ContainerAction.START -> {
                    dockerClient.startContainerCmd(containerId).exec()
                    Result.success("Container $containerId started")
                }
                ContainerAction.STOP -> {
                    dockerClient.stopContainerCmd(containerId).exec()
                    Result.success("Container $containerId stopped")
                }
                ContainerAction.RESTART -> {
                    dockerClient.restartContainerCmd(containerId).exec()
                    Result.success("Container $containerId restarted")
                }
                ContainerAction.REMOVE -> {
                    dockerClient.removeContainerCmd(containerId)
                        .withForce(true)
                        .exec()
                    Result.success("Container $containerId removed")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to execute $action on container $containerId", e)
            Result.failure(e)
        }
    }
}
