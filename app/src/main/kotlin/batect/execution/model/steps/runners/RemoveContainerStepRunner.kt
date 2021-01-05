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

package batect.execution.model.steps.runners

import batect.docker.api.ContainerRemovalFailedException
import batect.docker.client.ContainersClient
import batect.execution.model.events.ContainerRemovalFailedEvent
import batect.execution.model.events.ContainerRemovedEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.RemoveContainerStep

class RemoveContainerStepRunner(
    private val containersClient: ContainersClient
) {
    fun run(step: RemoveContainerStep, eventSink: TaskEventSink) {
        try {
            containersClient.remove(step.dockerContainer)
            eventSink.postEvent(ContainerRemovedEvent(step.container))
        } catch (e: ContainerRemovalFailedException) {
            eventSink.postEvent(ContainerRemovalFailedEvent(step.container, e.outputFromDocker))
        }
    }
}
