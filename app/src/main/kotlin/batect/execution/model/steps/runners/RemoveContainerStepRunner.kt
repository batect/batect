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

import batect.dockerclient.ContainerReference
import batect.dockerclient.ContainerRemovalFailedException
import batect.dockerclient.DockerClient
import batect.execution.model.events.ContainerRemovalFailedEvent
import batect.execution.model.events.ContainerRemovedEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.RemoveContainerStep
import batect.logging.Logger
import kotlinx.coroutines.runBlocking

class RemoveContainerStepRunner(
    private val client: DockerClient,
    private val logger: Logger
) {
    fun run(step: RemoveContainerStep, eventSink: TaskEventSink) {
        try {
            runBlocking {
                client.removeContainer(ContainerReference(step.dockerContainer.id), force = true, removeVolumes = true)
            }

            eventSink.postEvent(ContainerRemovedEvent(step.container))
        } catch (e: ContainerRemovalFailedException) {
            logger.error {
                message("Removing container failed.")
                exception(e)
                data("containerId", step.dockerContainer.id)
            }

            eventSink.postEvent(ContainerRemovalFailedEvent(step.container, e.message ?: ""))
        }
    }
}
