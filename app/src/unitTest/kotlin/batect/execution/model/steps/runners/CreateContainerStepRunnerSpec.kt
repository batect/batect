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

import batect.config.Container
import batect.config.VolumeMount
import batect.docker.ContainerCreationFailedException
import batect.docker.DockerContainer
import batect.docker.DockerContainerCreationRequest
import batect.docker.DockerContainerCreationRequestFactory
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.docker.DockerVolumeMount
import batect.docker.DockerVolumeMountSource
import batect.docker.UserAndGroup
import batect.docker.client.DockerContainersClient
import batect.execution.CleanupOption
import batect.execution.ContainerRuntimeConfiguration
import batect.execution.RunAsCurrentUserConfiguration
import batect.execution.RunAsCurrentUserConfigurationException
import batect.execution.RunAsCurrentUserConfigurationProvider
import batect.execution.RunOptions
import batect.execution.VolumeMountResolutionException
import batect.execution.VolumeMountResolver
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerCreationFailedEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.CreateContainerStep
import batect.testutils.createForEachTest
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.ui.containerio.ContainerIOStreamingOptions
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object CreateContainerStepRunnerSpec : Spek({
    describe("running a 'create container' step") {
        val container = Container("some-container", imageSourceDoesNotMatter())
        val otherContainer = Container("some-other-container", imageSourceDoesNotMatter())
        val image = DockerImage("some-image")
        val network = DockerNetwork("some-network")

        val config = mock<ContainerRuntimeConfiguration>()
        val step = CreateContainerStep(container, config, setOf(container, otherContainer), image, network)
        val request = mock<DockerContainerCreationRequest>()

        val containersClient by createForEachTest { mock<DockerContainersClient>() }

        val volumeMountResolver by createForEachTest {
            mock<VolumeMountResolver> {
                on { resolve(any<Set<VolumeMount>>()) } doReturn setOf(DockerVolumeMount(DockerVolumeMountSource.LocalPath("/local-container"), "/remote-container", "some-options-from-container"))
            }
        }

        val userAndGroup = UserAndGroup(456, 789)
        val runAsCurrentUserConfiguration = RunAsCurrentUserConfiguration(
            setOf(DockerVolumeMount(DockerVolumeMountSource.LocalPath("/local-user"), "/remote-user", "some-options-from-run-as-current-user")),
            userAndGroup
        )

        val runAsCurrentUserConfigurationProvider = mock<RunAsCurrentUserConfigurationProvider> {
            on { generateConfiguration(any(), any()) } doReturn runAsCurrentUserConfiguration
            on { determineUserAndGroup(any()) } doReturn userAndGroup
        }

        val runOptions = RunOptions("some-task", emptyList(), CleanupOption.Cleanup, CleanupOption.Cleanup, true, emptyMap())
        val combinedMounts = setOf(
            DockerVolumeMount(DockerVolumeMountSource.LocalPath("/local-container"), "/remote-container", "some-options-from-container"),
            DockerVolumeMount(DockerVolumeMountSource.LocalPath("/local-user"), "/remote-user", "some-options-from-run-as-current-user")
        )

        val creationRequestFactory by createForEachTest {
            mock<DockerContainerCreationRequestFactory> {
                on { create(container, image, network, config, combinedMounts, runOptions.propagateProxyEnvironmentVariables, runAsCurrentUserConfiguration.userAndGroup, "some-terminal", step.allContainersInNetwork, true, false) } doReturn request
            }
        }

        val ioStreamingOptions by createForEachTest {
            mock<ContainerIOStreamingOptions> {
                on { terminalTypeForContainer(container) } doReturn "some-terminal"
                on { attachStdinForContainer(container) } doReturn false
                on { useTTYForContainer(container) } doReturn true
            }
        }

        val eventSink by createForEachTest { mock<TaskEventSink>() }
        val runner by createForEachTest { CreateContainerStepRunner(containersClient, volumeMountResolver, runAsCurrentUserConfigurationProvider, creationRequestFactory, runOptions, ioStreamingOptions) }

        on("when creating the container succeeds") {
            val dockerContainer = DockerContainer("some-id")

            beforeEachTest {
                whenever(containersClient.create(request)).doReturn(dockerContainer)

                runner.run(step, eventSink)
            }

            it("creates the container with the provided configuration") {
                verify(containersClient).create(request)
            }

            it("creates any missing local volume mount directories before creating the container") {
                inOrder(runAsCurrentUserConfigurationProvider, containersClient) {
                    verify(runAsCurrentUserConfigurationProvider).createMissingVolumeMountDirectories(combinedMounts, container)
                    verify(containersClient).create(any())
                }
            }

            it("emits a 'container created' event") {
                verify(eventSink).postEvent(ContainerCreatedEvent(container, dockerContainer))
            }
        }

        on("when creating the container fails") {
            beforeEachTest {
                whenever(containersClient.create(request)).doThrow(ContainerCreationFailedException("Something went wrong."))

                runner.run(step, eventSink)
            }

            it("emits a 'container creation failed' event") {
                verify(eventSink).postEvent(ContainerCreationFailedEvent(container, "Something went wrong."))
            }
        }

        on("when resolving a volume mount fails") {
            beforeEachTest {
                whenever(volumeMountResolver.resolve(any<Set<VolumeMount>>())).doThrow(VolumeMountResolutionException("Something went wrong."))

                runner.run(step, eventSink)
            }

            it("emits a 'container creation failed' event") {
                verify(eventSink).postEvent(ContainerCreationFailedEvent(container, "Something went wrong."))
            }
        }

        on("when generating the 'run as current user' configuration fails") {
            beforeEachTest {
                whenever(runAsCurrentUserConfigurationProvider.generateConfiguration(any(), any())).doThrow(RunAsCurrentUserConfigurationException("Something went wrong."))

                runner.run(step, eventSink)
            }

            it("emits a 'container creation failed' event") {
                verify(eventSink).postEvent(ContainerCreationFailedEvent(container, "Something went wrong."))
            }
        }
    }
})
