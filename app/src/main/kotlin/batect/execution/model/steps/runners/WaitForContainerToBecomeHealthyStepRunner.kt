/*
   Copyright 2017-2020 Charles Korn.

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
import batect.docker.client.DockerContainersClient
import batect.docker.client.HealthStatus
import batect.execution.model.events.ContainerBecameHealthyEvent
import batect.execution.model.events.ContainerDidNotBecomeHealthyEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.WaitForContainerToBecomeHealthyStep
import batect.os.SystemInfo
import batect.primitives.CancellationContext

class WaitForContainerToBecomeHealthyStepRunner(
    private val containersClient: DockerContainersClient,
    private val cancellationContext: CancellationContext,
    private val systemInfo: SystemInfo
) {
    fun run(step: WaitForContainerToBecomeHealthyStep, eventSink: TaskEventSink) {
        try {
            val event = when (containersClient.waitForHealthStatus(step.dockerContainer, cancellationContext)) {
                HealthStatus.NoHealthCheck -> ContainerBecameHealthyEvent(step.container)
                HealthStatus.BecameHealthy -> ContainerBecameHealthyEvent(step.container)
                HealthStatus.BecameUnhealthy -> ContainerDidNotBecomeHealthyEvent(step.container, containerBecameUnhealthyMessage(step.dockerContainer))
                HealthStatus.Exited -> ContainerDidNotBecomeHealthyEvent(step.container, "The container exited before becoming healthy.")
            }

            eventSink.postEvent(event)
        } catch (e: ContainerHealthCheckException) {
            eventSink.postEvent(ContainerDidNotBecomeHealthyEvent(step.container, "Waiting for the container's health status failed: ${e.message}"))
        }
    }

    private fun containerBecameUnhealthyMessage(container: DockerContainer): String {
        val lastHealthCheckResult = containersClient.getLastHealthCheckResult(container)

        val message = when {
            lastHealthCheckResult.exitCode == 0 -> "The most recent health check exited with code 0, which usually indicates that the container became healthy just after the timeout period expired."
            lastHealthCheckResult.output.isEmpty() -> "The last health check exited with code ${lastHealthCheckResult.exitCode} but did not produce any output."
            else -> "The last health check exited with code ${lastHealthCheckResult.exitCode} and output:\n${lastHealthCheckResult.output.trim()}".replace("\n", systemInfo.lineSeparator)
        }

        return "The configured health check did not indicate that the container was healthy within the timeout period. $message"
    }
}
