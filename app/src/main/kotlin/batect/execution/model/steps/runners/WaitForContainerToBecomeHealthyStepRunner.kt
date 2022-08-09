/*
    Copyright 2017-2022 Charles Korn.

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

package batect.execution.model.steps.runners

import batect.docker.ContainerHealthCheckException
import batect.docker.DockerContainer
import batect.dockerclient.DockerClient
import batect.dockerclient.DockerClientException
import batect.dockerclient.Event
import batect.dockerclient.EventHandlerAction
import batect.execution.model.events.ContainerBecameHealthyEvent
import batect.execution.model.events.ContainerDidNotBecomeHealthyEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.WaitForContainerToBecomeHealthyStep
import batect.os.SystemInfo
import batect.primitives.CancellationContext
import batect.primitives.runBlocking
import kotlinx.datetime.Instant

class WaitForContainerToBecomeHealthyStepRunner(
    private val dockerClient: DockerClient,
    private val cancellationContext: CancellationContext,
    private val systemInfo: SystemInfo
) {
    fun run(step: WaitForContainerToBecomeHealthyStep, eventSink: TaskEventSink) {
        try {
            cancellationContext.runBlocking {
                if (!checkIfContainerHasHealthCheck(step.dockerContainer)) {
                    eventSink.postEvent(ContainerBecameHealthyEvent(step.container))
                    return@runBlocking
                }

                val event = when (waitForHealthStatus(step.dockerContainer)) {
                    HealthStatus.BecameHealthy -> ContainerBecameHealthyEvent(step.container)
                    HealthStatus.BecameUnhealthy -> ContainerDidNotBecomeHealthyEvent(step.container, containerBecameUnhealthyMessage(step.dockerContainer))
                    HealthStatus.Exited -> ContainerDidNotBecomeHealthyEvent(step.container, "The container exited before becoming healthy.")
                }

                eventSink.postEvent(event)
            }
        } catch (e: ContainerHealthCheckException) {
            eventSink.postEvent(ContainerDidNotBecomeHealthyEvent(step.container, "Waiting for the container's health status failed: ${e.message}"))
        } catch (e: DockerClientException) {
            eventSink.postEvent(ContainerDidNotBecomeHealthyEvent(step.container, "Waiting for the container's health status failed: ${e.message}"))
        }
    }

    private suspend fun checkIfContainerHasHealthCheck(container: DockerContainer): Boolean {
        val inspectionResult = dockerClient.inspectContainer(container.id)
        val healthcheck = inspectionResult.config.healthcheck

        return healthcheck != null && healthcheck.test.isNotEmpty()
    }

    private suspend fun waitForHealthStatus(container: DockerContainer): HealthStatus {
        val filters = mapOf(
            "event" to setOf("die", "health_status"),
            "container" to setOf(container.id)
        )

        var eventReceived: Event? = null

        dockerClient.streamEvents(beginningOfTime, null, filters) { event ->
            eventReceived = event
            EventHandlerAction.Stop
        }

        if (eventReceived == null) {
            throw ContainerHealthCheckException("Container did not emit any events.")
        }

        return when (val status = eventReceived!!.action) {
            "health_status: healthy" -> HealthStatus.BecameHealthy
            "health_status: unhealthy" -> HealthStatus.BecameUnhealthy
            "die" -> HealthStatus.Exited
            else -> throw ContainerHealthCheckException("Unexpected event received: $status")
        }
    }

    private suspend fun containerBecameUnhealthyMessage(container: DockerContainer): String {
        val inspectionResult = dockerClient.inspectContainer(container.id)
        val lastHealthCheckResult = inspectionResult.state.health!!.log.last()

        val message = when {
            lastHealthCheckResult.exitCode == 0L -> "The most recent health check exited with code 0, which usually indicates that the container became healthy just after the timeout period expired."
            lastHealthCheckResult.output.isEmpty() -> "The last health check exited with code ${lastHealthCheckResult.exitCode} but did not produce any output."
            else -> "The last health check exited with code ${lastHealthCheckResult.exitCode} and output:\n${lastHealthCheckResult.output.trim()}".replace("\n", systemInfo.lineSeparator)
        }

        return "The configured health check did not indicate that the container was healthy within the timeout period. $message"
    }

    companion object {
        private val beginningOfTime: Instant = Instant.fromEpochMilliseconds(0)
    }
}

private enum class HealthStatus {
    BecameHealthy,
    BecameUnhealthy,
    Exited
}
