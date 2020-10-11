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

import batect.docker.ContainerCreationFailedException
import batect.docker.DockerContainerCreationRequestFactory
import batect.docker.client.ContainersClient
import batect.execution.RunAsCurrentUserConfigurationException
import batect.execution.RunAsCurrentUserConfigurationProvider
import batect.execution.VolumeMountResolutionException
import batect.execution.VolumeMountResolver
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerCreationFailedEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.CreateContainerStep
import batect.ui.containerio.ContainerIOStreamingOptions

class CreateContainerStepRunner(
    private val containersClient: ContainersClient,
    private val volumeMountResolver: VolumeMountResolver,
    private val runAsCurrentUserConfigurationProvider: RunAsCurrentUserConfigurationProvider,
    private val creationRequestFactory: DockerContainerCreationRequestFactory,
    private val ioStreamingOptions: ContainerIOStreamingOptions
) {
    fun run(step: CreateContainerStep, eventSink: TaskEventSink) {
        try {
            val resolvedMounts = volumeMountResolver.resolve(step.container.volumeMounts)
            val runAsCurrentUserConfiguration = runAsCurrentUserConfigurationProvider.generateConfiguration(step.container, eventSink)
            val volumeMounts = resolvedMounts + runAsCurrentUserConfiguration.volumeMounts

            runAsCurrentUserConfigurationProvider.createMissingVolumeMountDirectories(volumeMounts, step.container)

            val creationRequest = creationRequestFactory.create(
                step.container,
                step.image,
                step.network,
                step.config,
                volumeMounts,
                runAsCurrentUserConfiguration.userAndGroup,
                ioStreamingOptions.terminalTypeForContainer(step.container),
                ioStreamingOptions.useTTYForContainer(step.container),
                ioStreamingOptions.attachStdinForContainer(step.container)
            )

            val dockerContainer = containersClient.create(creationRequest)
            eventSink.postEvent(ContainerCreatedEvent(step.container, dockerContainer))
        } catch (e: ContainerCreationFailedException) {
            eventSink.postEvent(ContainerCreationFailedEvent(step.container, e.message ?: ""))
        } catch (e: VolumeMountResolutionException) {
            eventSink.postEvent(ContainerCreationFailedEvent(step.container, e.message ?: ""))
        } catch (e: RunAsCurrentUserConfigurationException) {
            eventSink.postEvent(ContainerCreationFailedEvent(step.container, e.message ?: ""))
        }
    }
}
