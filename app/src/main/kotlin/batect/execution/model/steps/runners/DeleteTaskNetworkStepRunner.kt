/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.execution.model.steps.runners

import batect.dockerclient.DockerClient
import batect.dockerclient.NetworkDeletionFailedException
import batect.dockerclient.NetworkReference
import batect.execution.model.events.TaskEventSink
import batect.execution.model.events.TaskNetworkDeletedEvent
import batect.execution.model.events.TaskNetworkDeletionFailedEvent
import batect.execution.model.steps.DeleteTaskNetworkStep
import batect.logging.Logger
import kotlinx.coroutines.runBlocking

class DeleteTaskNetworkStepRunner(
    private val client: DockerClient,
    private val logger: Logger,
) {
    fun run(step: DeleteTaskNetworkStep, eventSink: TaskEventSink) {
        try {
            runBlocking {
                client.deleteNetwork(NetworkReference(step.network.id))
            }

            eventSink.postEvent(TaskNetworkDeletedEvent)
        } catch (e: NetworkDeletionFailedException) {
            logger.error {
                message("Deleting task network failed.")
                exception(e)
            }

            eventSink.postEvent(TaskNetworkDeletionFailedEvent(e.message ?: ""))
        }
    }
}
