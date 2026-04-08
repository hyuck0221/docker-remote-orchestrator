package com.orchestrator.client.docker

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.model.Frame
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.slf4j.LoggerFactory

class LogStreamer(private val dockerClient: DockerClient) {

    private val logger = LoggerFactory.getLogger(LogStreamer::class.java)

    fun streamLogs(containerId: String, tail: Int = 100): Flow<String> = callbackFlow {
        val callback = dockerClient.logContainerCmd(containerId)
            .withStdOut(true)
            .withStdErr(true)
            .withFollowStream(true)
            .withTail(tail)
            .exec(object : ResultCallback.Adapter<Frame>() {
                override fun onNext(frame: Frame?) {
                    frame?.payload?.let { bytes ->
                        val line = String(bytes).trimEnd()
                        if (line.isNotBlank()) {
                            trySend(line)
                        }
                    }
                }

                override fun onError(throwable: Throwable?) {
                    if (throwable is java.nio.channels.ClosedByInterruptException ||
                        throwable is java.io.IOException) {
                        logger.debug("Log stream closed for container $containerId")
                    } else {
                        logger.warn("Log stream error for container $containerId: ${throwable?.message}")
                    }
                    close(throwable as? Exception)
                }

                override fun onComplete() {
                    close()
                }
            })

        awaitClose {
            try {
                callback.close()
            } catch (_: Exception) {}
        }
    }
}
