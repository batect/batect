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

import batect.docker.api.NetworkCreationFailedException
import batect.docker.client.DockerContainerType
import batect.docker.client.DockerNetworksClient
import batect.execution.model.events.TaskEventSink
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.events.TaskNetworkCreationFailedEvent
import batect.execution.model.steps.CreateTaskNetworkStep

class CreateTaskNetworkStepRunner(
    val networksClient: DockerNetworksClient
) {
    fun run(step: CreateTaskNetworkStep, eventSink: TaskEventSink) {
        try {
            val driver = when (step.containerType) {
                DockerContainerType.Linux -> "bridge"
                DockerContainerType.Windows -> "nat"
            }

            val network = networksClient.create(driver)
            eventSink.postEvent(TaskNetworkCreatedEvent(network))
        } catch (e: NetworkCreationFailedException) {
            eventSink.postEvent(TaskNetworkCreationFailedEvent(e.outputFromDocker))
        }
    }
}
