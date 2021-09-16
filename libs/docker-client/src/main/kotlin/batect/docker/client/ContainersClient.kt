/*
    Copyright 2017-2021 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.docker.client

import batect.docker.ContainerCreationRequest
import batect.docker.ContainerFilesystemItem
import batect.docker.ContainerHealthCheckException
import batect.docker.DockerContainer
import batect.docker.DockerContainerInfo
import batect.docker.DockerContainerRunResult
import batect.docker.DockerException
import batect.docker.DockerHealthCheckResult
import batect.docker.api.ContainerInspectionFailedException
import batect.docker.api.ContainersAPI
import batect.docker.data
import batect.docker.run.ContainerIOStreamer
import batect.docker.run.ContainerTTYManager
import batect.docker.run.ContainerWaiter
import batect.docker.run.InputConnection
import batect.docker.run.OutputConnection
import batect.logging.Logger
import batect.os.ConsoleManager
import batect.os.Dimensions
import batect.primitives.CancellationContext
import okio.Sink
import okio.Source
import java.time.Duration

class ContainersClient(
    private val api: ContainersAPI,
    private val consoleManager: ConsoleManager,
    private val waiter: ContainerWaiter,
    private val ioStreamer: ContainerIOStreamer,
    private val ttyManager: ContainerTTYManager,
    private val logger: Logger
) {
    fun create(creationRequest: ContainerCreationRequest): DockerContainer = api.create(creationRequest)
    fun stop(container: DockerContainer) = api.stop(container)
    fun remove(container: DockerContainer) = api.remove(container)
    fun upload(container: DockerContainer, source: Set<ContainerFilesystemItem>, destination: String) = api.upload(container, source, destination)

    fun run(container: DockerContainer, stdout: Sink?, stdin: Source?, usesTTY: Boolean, cancellationContext: CancellationContext, frameDimensions: Dimensions, onStarted: () -> Unit): DockerContainerRunResult {
        logger.info {
            message("Running container.")
            data("container", container)
        }

        if (stdout == null && stdin != null) {
            throw DockerException("Attempted to stream input to container without streaming container output.")
        }

        val exitCodeSource = waiter.startWaitingForContainerToExit(container, cancellationContext)

        connectContainerOutput(container, stdout, usesTTY).use { outputConnection ->
            connectContainerInput(container, stdin).use { inputConnection ->
                api.start(container)
                onStarted()

                cancellationContext.addCancellationCallback { onRunCancelled(container) }.use {
                    ttyManager.monitorForSizeChanges(container, frameDimensions).use {
                        startRawModeIfRequired(stdin, usesTTY).use {
                            ioStreamer.stream(outputConnection, inputConnection, cancellationContext)
                        }
                    }
                }
            }
        }

        val exitCode = exitCodeSource.get()

        logger.info {
            message("Container exited.")
            data("container", container)
            data("exitCode", exitCode)
        }

        return DockerContainerRunResult(exitCode)
    }

    private fun connectContainerOutput(container: DockerContainer, stdout: Sink?, isTTY: Boolean): OutputConnection {
        if (stdout == null) {
            return OutputConnection.Disconnected
        }

        return OutputConnection.Connected(api.attachToOutput(container, isTTY), stdout)
    }

    private fun connectContainerInput(container: DockerContainer, stdin: Source?): InputConnection {
        if (stdin == null) {
            return InputConnection.Disconnected
        }

        return InputConnection.Connected(stdin, api.attachToInput(container))
    }

    private fun startRawModeIfRequired(stdin: Source?, isTTY: Boolean): AutoCloseable {
        if (stdin == null || !isTTY) {
            return AutoCloseable { }
        }

        return consoleManager.enterRawMode()
    }

    private fun onRunCancelled(container: DockerContainer) {
        logger.info {
            message("Run cancelled, stopping container.")
            data("container", container)
        }

        api.stop(container)

        logger.info {
            message("Container stopped successfully.")
            data("container", container)
        }
    }

    fun waitForHealthStatus(container: DockerContainer, cancellationContext: CancellationContext): HealthStatus {
        logger.info {
            message("Checking health status of container.")
            data("container", container)
        }

        try {
            val info = api.inspect(container)

            if (!hasHealthCheck(info)) {
                logger.warn {
                    message("Container has no health check.")
                }

                return HealthStatus.NoHealthCheck
            }

            val healthCheckInfo = info.config.healthCheck
            val checkPeriod = (healthCheckInfo.interval + healthCheckInfo.timeout).multipliedBy(healthCheckInfo.retries.toLong())
            val overheadMargin = Duration.ofSeconds(1)
            val timeout = healthCheckInfo.startPeriod + checkPeriod + overheadMargin
            val event = api.waitForNextEvent(container, setOf("die", "health_status"), timeout, cancellationContext)

            logger.info {
                message("Received event notification from Docker.")
                data("event", event)
            }

            return when {
                event.status == "health_status: healthy" -> HealthStatus.BecameHealthy
                event.status == "health_status: unhealthy" -> HealthStatus.BecameUnhealthy
                event.status == "die" -> HealthStatus.Exited
                else -> throw ContainerHealthCheckException("Unexpected event received: ${event.status}")
            }
        } catch (e: ContainerInspectionFailedException) {
            throw ContainerHealthCheckException("Checking if container '${container.id}' has a health check failed: ${e.message}", e)
        } catch (e: DockerException) {
            throw ContainerHealthCheckException("Waiting for health status of container '${container.id}' failed: ${e.message}", e)
        }
    }

    private fun hasHealthCheck(info: DockerContainerInfo): Boolean = info.config.healthCheck.test != null

    fun getLastHealthCheckResult(container: DockerContainer): DockerHealthCheckResult {
        try {
            val info = api.inspect(container)

            if (info.state.health == null) {
                throw ContainerHealthCheckException("Could not get the last health check result for container '${container.id}'. The container does not have a health check.")
            }

            return info.state.health.log.last()
        } catch (e: ContainerInspectionFailedException) {
            throw ContainerHealthCheckException("Could not get the last health check result for container '${container.id}': ${e.message}", e)
        }
    }
}

enum class HealthStatus {
    NoHealthCheck,
    BecameHealthy,
    BecameUnhealthy,
    Exited
}
