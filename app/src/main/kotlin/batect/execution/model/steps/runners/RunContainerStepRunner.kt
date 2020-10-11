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

import batect.docker.DockerException
import batect.docker.client.ContainersClient
import batect.execution.model.events.ContainerRunFailedEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.RunningContainerExitedEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.RunContainerStep
import batect.primitives.CancellationContext
import batect.ui.containerio.ContainerIOStreamingOptions

class RunContainerStepRunner(
    private val containersClient: ContainersClient,
    private val ioStreamingOptions: ContainerIOStreamingOptions,
    private val cancellationContext: CancellationContext
) {
    fun run(step: RunContainerStep, eventSink: TaskEventSink) {
        try {
            val stdout = ioStreamingOptions.stdoutForContainer(step.container)
            val stdin = ioStreamingOptions.stdinForContainer(step.container)

            val result = containersClient.run(step.dockerContainer, stdout, stdin, ioStreamingOptions.useTTYForContainer(step.container), cancellationContext, ioStreamingOptions.frameDimensions) {
                eventSink.postEvent(ContainerStartedEvent(step.container))
            }

            eventSink.postEvent(RunningContainerExitedEvent(step.container, result.exitCode))
        } catch (e: DockerException) {
            eventSink.postEvent(ContainerRunFailedEvent(step.container, e.message ?: ""))
        }
    }
}
