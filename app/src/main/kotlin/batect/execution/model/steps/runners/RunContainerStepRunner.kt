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
import batect.dockerclient.DockerClientException
import batect.dockerclient.ReadyNotification
import batect.execution.model.events.ContainerRunFailedEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.RunningContainerExitedEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.RunContainerStep
import batect.logging.Logger
import batect.primitives.CancellationContext
import batect.primitives.runBlocking
import batect.ui.containerio.ContainerIOStreamingOptions
import kotlinx.coroutines.launch

class RunContainerStepRunner(
    private val client: DockerClient,
    private val ioStreamingOptions: ContainerIOStreamingOptions,
    private val cancellationContext: CancellationContext,
    private val logger: Logger,
) {
    fun run(step: RunContainerStep, eventSink: TaskEventSink) {
        try {
            val stdout = ioStreamingOptions.stdoutForContainer(step.container)
            val stdin = ioStreamingOptions.stdinForContainer(step.container)

            cancellationContext.runBlocking {
                val startedNotification = ReadyNotification()

                launch {
                    startedNotification.waitForReady()

                    eventSink.postEvent(ContainerStartedEvent(step.container))
                }

                val exitCode = client.run(
                    step.dockerContainer.reference,
                    stdout,
                    stdout,
                    stdin,
                    startedNotification,
                )

                eventSink.postEvent(RunningContainerExitedEvent(step.container, exitCode))
            }
        } catch (e: DockerClientException) {
            logger.error {
                message("Running container failed.")
                exception(e)
                data("containerId", step.dockerContainer.reference.id)
            }

            eventSink.postEvent(ContainerRunFailedEvent(step.container, e.message ?: ""))
        }
    }
}
