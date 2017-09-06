/*
   Copyright 2017 Charles Korn.

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

package batect.model.steps

import batect.docker.ContainerCreationFailedException
import batect.docker.ContainerDoesNotExistException
import batect.docker.ContainerHealthCheckException
import batect.docker.ContainerRemovalFailedException
import batect.docker.ContainerStartFailedException
import batect.docker.ContainerStopFailedException
import batect.docker.DockerClient
import batect.docker.HealthStatus
import batect.docker.ImageBuildFailedException
import batect.docker.NetworkCreationFailedException
import batect.docker.NetworkDeletionFailedException
import batect.model.events.ContainerBecameHealthyEvent
import batect.model.events.ContainerCreatedEvent
import batect.model.events.ContainerCreationFailedEvent
import batect.model.events.ContainerDidNotBecomeHealthyEvent
import batect.model.events.ContainerRemovalFailedEvent
import batect.model.events.ContainerRemovedEvent
import batect.model.events.ContainerStartFailedEvent
import batect.model.events.ContainerStartedEvent
import batect.model.events.ContainerStopFailedEvent
import batect.model.events.ContainerStoppedEvent
import batect.model.events.ImageBuildFailedEvent
import batect.model.events.ImageBuiltEvent
import batect.model.events.RunningContainerExitedEvent
import batect.model.events.TaskEventSink
import batect.model.events.TaskNetworkCreatedEvent
import batect.model.events.TaskNetworkCreationFailedEvent
import batect.model.events.TaskNetworkDeletedEvent
import batect.model.events.TaskNetworkDeletionFailedEvent
import batect.model.events.TaskStartedEvent

class TaskStepRunner(private val dockerClient: DockerClient) {
    fun run(step: TaskStep, eventSink: TaskEventSink) {
        when (step) {
            is BeginTaskStep -> eventSink.postEvent(TaskStartedEvent)
            is BuildImageStep -> handleBuildImageStep(step, eventSink)
            is CreateTaskNetworkStep -> handleCreateTaskNetworkStep(eventSink)
            is CreateContainerStep -> handleCreateContainerStep(step, eventSink)
            is RunContainerStep -> handleRunContainerStep(step, eventSink)
            is StartContainerStep -> handleStartContainerStep(step, eventSink)
            is WaitForContainerToBecomeHealthyStep -> handleWaitForContainerToBecomeHealthyStep(step, eventSink)
            is StopContainerStep -> handleStopContainerStep(step, eventSink)
            is CleanUpContainerStep -> handleCleanUpContainerStep(step, eventSink)
            is RemoveContainerStep -> handleRemoveContainerStep(step, eventSink)
            is DeleteTaskNetworkStep -> handleDeleteTaskNetworkStep(step, eventSink)
            is DisplayTaskFailureStep -> ignore()
            is FinishTaskStep -> ignore()
        }
    }

    private fun handleBuildImageStep(step: BuildImageStep, eventSink: TaskEventSink) {
        try {
            val image = dockerClient.build(step.projectName, step.container)
            eventSink.postEvent(ImageBuiltEvent(step.container, image))
        } catch (e: ImageBuildFailedException) {
            eventSink.postEvent(ImageBuildFailedEvent(step.container, e.message ?: ""))
        }
    }

    private fun handleCreateTaskNetworkStep(eventSink: TaskEventSink) {
        try {
            val network = dockerClient.createNewBridgeNetwork()
            eventSink.postEvent(TaskNetworkCreatedEvent(network))
        } catch (e: NetworkCreationFailedException) {
            eventSink.postEvent(TaskNetworkCreationFailedEvent(e.outputFromDocker))
        }
    }

    private fun handleCreateContainerStep(step: CreateContainerStep, eventSink: TaskEventSink) {
        try {
            val dockerContainer = dockerClient.create(step.container, step.command, step.image, step.network)
            eventSink.postEvent(ContainerCreatedEvent(step.container, dockerContainer))
        } catch (e: ContainerCreationFailedException) {
            eventSink.postEvent(ContainerCreationFailedEvent(step.container, e.message ?: ""))
        }
    }

    private fun handleRunContainerStep(step: RunContainerStep, eventSink: TaskEventSink) {
        val result = dockerClient.run(step.dockerContainer)
        eventSink.postEvent(RunningContainerExitedEvent(step.container, result.exitCode))
    }

    private fun handleStartContainerStep(step: StartContainerStep, eventSink: TaskEventSink) {
        try {
            dockerClient.start(step.dockerContainer)
            eventSink.postEvent(ContainerStartedEvent(step.container))
        } catch (e: ContainerStartFailedException) {
            eventSink.postEvent(ContainerStartFailedEvent(step.container, e.outputFromDocker))
        }
    }

    private fun handleWaitForContainerToBecomeHealthyStep(step: WaitForContainerToBecomeHealthyStep, eventSink: TaskEventSink) {
        try {
            val result = dockerClient.waitForHealthStatus(step.dockerContainer)

            val event = when (result) {
                HealthStatus.NoHealthCheck -> ContainerBecameHealthyEvent(step.container)
                HealthStatus.BecameHealthy -> ContainerBecameHealthyEvent(step.container)
                HealthStatus.BecameUnhealthy -> ContainerDidNotBecomeHealthyEvent(step.container, "The configured health check did not report the container as healthy within the timeout period.")
                HealthStatus.Exited -> ContainerDidNotBecomeHealthyEvent(step.container, "The container exited before becoming healthy.")
            }

            eventSink.postEvent(event)
        } catch (e: ContainerHealthCheckException) {
            eventSink.postEvent(ContainerDidNotBecomeHealthyEvent(step.container, "Waiting for the container's health status failed: ${e.message}"))
        }
    }

    private fun handleStopContainerStep(step: StopContainerStep, eventSink: TaskEventSink) {
        try {
            dockerClient.stop(step.dockerContainer)
            eventSink.postEvent(ContainerStoppedEvent(step.container))
        } catch (e: ContainerStopFailedException) {
            eventSink.postEvent(ContainerStopFailedEvent(step.container, e.outputFromDocker))
        }
    }

    private fun handleCleanUpContainerStep(step: CleanUpContainerStep, eventSink: TaskEventSink) {
        try {
            dockerClient.forciblyRemove(step.dockerContainer)
            eventSink.postEvent(ContainerRemovedEvent(step.container))
        } catch (e: ContainerRemovalFailedException) {
            eventSink.postEvent(ContainerRemovalFailedEvent(step.container, e.outputFromDocker))
        } catch (_: ContainerDoesNotExistException) {
            eventSink.postEvent(ContainerRemovedEvent(step.container))
        }
    }

    private fun handleRemoveContainerStep(step: RemoveContainerStep, eventSink: TaskEventSink) {
        try {
            dockerClient.remove(step.dockerContainer)
            eventSink.postEvent(ContainerRemovedEvent(step.container))
        } catch (e: ContainerRemovalFailedException) {
            eventSink.postEvent(ContainerRemovalFailedEvent(step.container, e.outputFromDocker))
        } catch (_: ContainerDoesNotExistException) {
            eventSink.postEvent(ContainerRemovedEvent(step.container))
        }
    }

    private fun handleDeleteTaskNetworkStep(step: DeleteTaskNetworkStep, eventSink: TaskEventSink) {
        try {
            dockerClient.deleteNetwork(step.network)
            eventSink.postEvent(TaskNetworkDeletedEvent)
        } catch (e: NetworkDeletionFailedException) {
            eventSink.postEvent(TaskNetworkDeletionFailedEvent(e.outputFromDocker))
        }
    }

    private fun ignore() {
        // Do nothing.
    }
}
