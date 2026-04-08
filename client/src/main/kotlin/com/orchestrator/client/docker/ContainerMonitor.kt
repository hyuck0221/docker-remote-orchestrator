package com.orchestrator.client.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Event
import com.orchestrator.common.model.ContainerInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.slf4j.LoggerFactory
import java.io.Closeable

class ContainerMonitor(
    private val dockerClient: DockerClient,
    private val containerService: ContainerService,
    private val pollIntervalMs: Long = 5000L
) {
    private val logger = LoggerFactory.getLogger(ContainerMonitor::class.java)

    private val _containers = MutableStateFlow<List<ContainerInfo>>(emptyList())
    val containers: StateFlow<List<ContainerInfo>> = _containers.asStateFlow()

    private var monitorJob: Job? = null
    private var eventCallback: Closeable? = null

    fun start(scope: CoroutineScope) {
        monitorJob = scope.launch {
            // Initial fetch
            refreshContainers()

            // Start Docker events listener
            startEventListener(scope)

            // Periodic polling for stats (CPU/Memory)
            while (isActive) {
                delay(pollIntervalMs)
                refreshContainers()
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        try {
            eventCallback?.close()
        } catch (_: Exception) {}
        eventCallback = null
    }

    private fun refreshContainers() {
        val updated = containerService.listContainers()
        _containers.value = updated
    }

    private fun startEventListener(scope: CoroutineScope) {
        try {
            eventCallback = dockerClient.eventsCmd()
                .withEventTypeFilter("container")
                .exec(object : ResultCallback.Adapter<Event>() {
                    override fun onNext(event: Event?) {
                        event ?: return
                        logger.debug("Docker event: {} {}", event.action, event.id?.take(12))
                        scope.launch {
                            delay(500) // Small delay to let Docker state settle
                            refreshContainers()
                        }
                    }

                    override fun onError(throwable: Throwable?) {
                        if (throwable is java.nio.channels.ClosedByInterruptException) {
                            logger.debug("Docker events stream closed")
                        } else {
                            logger.warn("Docker events stream error, relying on polling: ${throwable?.message}")
                        }
                    }
                })
            logger.info("Docker events listener started")
        } catch (e: Exception) {
            logger.warn("Failed to start Docker events listener, using polling only", e)
        }
    }
}
