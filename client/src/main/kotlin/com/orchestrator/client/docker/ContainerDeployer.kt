package com.orchestrator.client.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.*
import com.orchestrator.common.model.DeployConfig
import com.orchestrator.common.protocol.DeployPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File

class ContainerDeployer(private val dockerClient: DockerClient) {

    private val logger = LoggerFactory.getLogger(ContainerDeployer::class.java)

    suspend fun deploy(
        commandId: String,
        config: DeployConfig,
        onProgress: suspend (DeployPhase, String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Pull image (with registry auth if available)
            onProgress(DeployPhase.PULLING, "Pulling ${config.image}...")
            logger.info("[$commandId] Pulling image: ${config.image}")
            try {
                val pullCmd = dockerClient.pullImageCmd(config.image)
                val authConfig = resolveAuthConfig(config.image)
                if (authConfig != null) {
                    pullCmd.withAuthConfig(authConfig)
                    logger.info("[$commandId] Using auth for registry: ${authConfig.registryAddress}")
                }
                pullCmd.start().awaitCompletion()
                logger.info("[$commandId] Image pulled: ${config.image}")
            } catch (pullError: Exception) {
                // Check if image exists locally — if so, continue with local image
                val localImages = dockerClient.listImagesCmd().withImageNameFilter(config.image).exec()
                if (localImages.isNotEmpty()) {
                    logger.warn("[$commandId] Pull failed but image exists locally, continuing: ${pullError.message}")
                    onProgress(DeployPhase.PULLING, "Using local image (pull failed)")
                } else {
                    throw pullError
                }
            }

            // 2. Remove existing container with same name if exists
            if (!config.containerName.isNullOrBlank()) {
                try {
                    val existing = dockerClient.listContainersCmd()
                        .withShowAll(true)
                        .withNameFilter(listOf(config.containerName))
                        .exec()
                        .filter { c -> c.names?.any { it.removePrefix("/") == config.containerName } == true }
                    if (existing.isNotEmpty()) {
                        val existingId = existing.first().id
                        onProgress(DeployPhase.CREATING, "Removing existing container ${config.containerName}...")
                        logger.info("[$commandId] Removing existing container: ${config.containerName} ($existingId)")
                        try { dockerClient.stopContainerCmd(existingId).exec() } catch (_: Exception) {}
                        dockerClient.removeContainerCmd(existingId).withForce(true).exec()
                    }
                } catch (e: Exception) {
                    logger.debug("[$commandId] No existing container to remove: ${e.message}")
                }
            }

            // 3. Create new container
            onProgress(DeployPhase.CREATING, "Creating container...")
            logger.info("[$commandId] Creating container from ${config.image}")
            val createCmd = dockerClient.createContainerCmd(config.image).apply {
                config.containerName?.let { withName(it) }
                if (config.env.isNotEmpty()) withEnv(config.env)
                if (config.labels.isNotEmpty()) withLabels(config.labels)
                withHostConfig(buildHostConfig(config))
                val exposedPorts = config.ports.map { ExposedPort(it.privatePort, InternetProtocol.valueOf(it.type.uppercase())) }
                if (exposedPorts.isNotEmpty()) withExposedPorts(exposedPorts)
            }
            val createResponse = createCmd.exec()
            val containerId = createResponse.id.take(12)
            logger.info("[$commandId] Container created: $containerId")

            // 3. Start container
            onProgress(DeployPhase.STARTING, "Starting container...")
            dockerClient.startContainerCmd(createResponse.id).exec()
            logger.info("[$commandId] Container started: $containerId")

            onProgress(DeployPhase.COMPLETE, "Deployed successfully")
            Result.success(containerId)
        } catch (e: Exception) {
            logger.error("[$commandId] Deploy failed", e)
            onProgress(DeployPhase.FAILED, e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    private fun resolveAuthConfig(image: String): AuthConfig? {
        try {
            val registry = extractRegistry(image) ?: return null
            val dockerConfigDir = File(System.getProperty("user.home"), ".docker")
            val configFile = File(dockerConfigDir, "config.json")
            if (!configFile.exists()) return null

            val configJson = configFile.readText()

            // 1. Try credHelpers (e.g., ecr-login, gcloud, osxkeychain)
            val credHelpersMatch = Regex("\"credHelpers\"\\s*:\\s*\\{([^}]*)\\}").find(configJson)
            if (credHelpersMatch != null) {
                val helpers = credHelpersMatch.groupValues[1]
                val helperMatch = Regex("\"([^\"]*${Regex.escape(registry)}[^\"]*)\"\\s*:\\s*\"([^\"]+)\"").find(helpers)
                    ?: Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"").findAll(helpers)
                        .firstOrNull { registry.contains(it.groupValues[1]) || it.groupValues[1].contains(registry) }

                if (helperMatch != null) {
                    val helperName = helperMatch.groupValues[2]
                    val creds = invokeCredentialHelper(helperName, registry)
                    if (creds != null) return creds
                }
            }

            // 2. Try credsStore (global credential store)
            val credsStoreMatch = Regex("\"credsStore\"\\s*:\\s*\"([^\"]+)\"").find(configJson)
            if (credsStoreMatch != null) {
                val storeName = credsStoreMatch.groupValues[1]
                val creds = invokeCredentialHelper(storeName, registry)
                if (creds != null) return creds
            }

            // 3. Try auths (base64-encoded inline credentials)
            val authsMatch = Regex("\"auths\"\\s*:\\s*\\{([^}]*)\\}").find(configJson)
            if (authsMatch != null) {
                val auths = authsMatch.groupValues[1]
                val authMatch = Regex("\"([^\"]*${Regex.escape(registry)}[^\"]*)\"\\s*:\\s*\\{[^}]*\"auth\"\\s*:\\s*\"([^\"]+)\"").find(auths)
                if (authMatch != null) {
                    val decoded = String(java.util.Base64.getDecoder().decode(authMatch.groupValues[2]))
                    val parts = decoded.split(":", limit = 2)
                    if (parts.size == 2) {
                        return AuthConfig()
                            .withRegistryAddress(registry)
                            .withUsername(parts[0])
                            .withPassword(parts[1])
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("Failed to resolve auth config for $image: ${e.message}")
        }
        return null
    }

    private fun invokeCredentialHelper(helperName: String, registry: String): AuthConfig? {
        return try {
            val process = ProcessBuilder("docker-credential-$helperName", "get")
                .redirectErrorStream(true)
                .start()
            process.outputStream.write(registry.toByteArray())
            process.outputStream.close()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                logger.debug("Credential helper $helperName failed (exit=$exitCode): $output")
                return null
            }

            // Parse JSON response: {"Username":"...","Secret":"..."}
            val username = Regex("\"Username\"\\s*:\\s*\"([^\"]+)\"").find(output)?.groupValues?.get(1)
            val secret = Regex("\"Secret\"\\s*:\\s*\"([^\"]+)\"").find(output)?.groupValues?.get(1)
            if (username != null && secret != null) {
                logger.info("Got credentials from docker-credential-$helperName for $registry")
                AuthConfig()
                    .withRegistryAddress(registry)
                    .withUsername(username)
                    .withPassword(secret)
            } else null
        } catch (e: Exception) {
            logger.debug("Failed to invoke docker-credential-$helperName: ${e.message}")
            null
        }
    }

    private fun extractRegistry(image: String): String? {
        // Images like "nginx" or "library/nginx" are from Docker Hub — no private auth needed
        // Images like "123456.dkr.ecr.region.amazonaws.com/repo:tag" have a registry prefix
        val parts = image.split("/")
        if (parts.size < 2) return null
        val first = parts[0]
        // A registry contains a dot or colon (e.g., domain.com, localhost:5000)
        return if (first.contains(".") || first.contains(":")) first else null
    }

    private fun buildHostConfig(config: DeployConfig): HostConfig {
        val hostConfig = HostConfig.newHostConfig()

        // Port bindings
        if (config.ports.isNotEmpty()) {
            val portBindings = Ports()
            config.ports.forEach { pm ->
                val exposedPort = ExposedPort(pm.privatePort, InternetProtocol.valueOf(pm.type.uppercase()))
                val pubPort = pm.publicPort
                val binding = if (pubPort != null) {
                    Ports.Binding.bindPort(pubPort)
                } else {
                    Ports.Binding.empty()
                }
                portBindings.bind(exposedPort, binding)
            }
            hostConfig.withPortBindings(portBindings)
        }

        // Volumes
        if (config.volumes.isNotEmpty()) {
            val binds = config.volumes.map { Bind.parse(it) }
            hostConfig.withBinds(binds)
        }

        // Restart policy
        val restartPolicy = when (config.restartPolicy) {
            "always" -> RestartPolicy.alwaysRestart()
            "unless-stopped" -> RestartPolicy.unlessStoppedRestart()
            "on-failure" -> RestartPolicy.onFailureRestart(3)
            else -> RestartPolicy.noRestart()
        }
        hostConfig.withRestartPolicy(restartPolicy)

        return hostConfig
    }
}
