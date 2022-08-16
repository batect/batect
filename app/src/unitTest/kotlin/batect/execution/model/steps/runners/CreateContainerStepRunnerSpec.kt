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

import batect.config.Container
import batect.config.ExpressionEvaluationException
import batect.config.VolumeMount
import batect.docker.DockerContainer
import batect.docker.DockerContainerCreationRequestFactory
import batect.dockerclient.ContainerCreationFailedException
import batect.dockerclient.ContainerCreationSpec
import batect.dockerclient.ContainerReference
import batect.dockerclient.DockerClient
import batect.dockerclient.HostMount
import batect.dockerclient.ImageReference
import batect.dockerclient.NetworkReference
import batect.dockerclient.UserAndGroup
import batect.execution.RunAsCurrentUserConfigurationException
import batect.execution.RunAsCurrentUserConfigurationProvider
import batect.execution.VolumeMountResolutionException
import batect.execution.VolumeMountResolver
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerCreationFailedEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.CreateContainerStep
import batect.testutils.beforeEachTestSuspend
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.itSuspend
import batect.testutils.on
import batect.ui.containerio.ContainerIOStreamingOptions
import okio.Path.Companion.toPath
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object CreateContainerStepRunnerSpec : Spek({
    describe("running a 'create container' step") {
        val container = Container("some-container", imageSourceDoesNotMatter())
        val image = ImageReference("some-image")
        val network = NetworkReference("some-network")

        val step = CreateContainerStep(container, image, network)
        val spec = mock<ContainerCreationSpec> {
            on { name } doReturn "some-container-name"
        }

        val dockerContainer = DockerContainer(ContainerReference("some-id"), "some-container-name")
        val dockerClient by createForEachTest {
            mock<DockerClient> {
                onBlocking { createContainer(any()) } doReturn ContainerReference("some-id")
            }
        }

        val resolvedMounts = setOf(HostMount("/local-container".toPath(), "/remote-container", "some-options-from-container"))

        val volumeMountResolver by createForEachTest {
            mock<VolumeMountResolver> {
                on { resolve(any<Set<VolumeMount>>()) } doReturn resolvedMounts
            }
        }

        val userAndGroup = UserAndGroup(456, 789)

        val runAsCurrentUserConfigurationProvider by createForEachTest {
            mock<RunAsCurrentUserConfigurationProvider> {
                on { determineUserAndGroup(any()) } doReturn userAndGroup
            }
        }

        val creationRequestFactory by createForEachTest {
            mock<DockerContainerCreationRequestFactory> {
                on { create(container, ImageReference("some-image"), NetworkReference("some-network"), resolvedMounts, userAndGroup, "some-terminal", true, false) } doReturn spec
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
        val logger by createLoggerForEachTest()
        val runner by createForEachTest { CreateContainerStepRunner(dockerClient, volumeMountResolver, runAsCurrentUserConfigurationProvider, creationRequestFactory, ioStreamingOptions, logger) }

        on("when creating the container succeeds") {
            beforeEachTest {
                runner.run(step, eventSink)
            }

            itSuspend("creates the container with the provided configuration") {
                verify(dockerClient).createContainer(spec)
            }

            itSuspend("creates any missing local volume mount directories before creating the container") {
                inOrder(runAsCurrentUserConfigurationProvider, dockerClient) {
                    verify(runAsCurrentUserConfigurationProvider).createMissingVolumeMountDirectories(resolvedMounts, container)
                    verify(dockerClient).createContainer(any())
                }
            }

            it("emits a 'container created' event") {
                verify(eventSink).postEvent(ContainerCreatedEvent(container, dockerContainer))
            }

            it("applies any configuration required for 'run as current user' mode") {
                verify(runAsCurrentUserConfigurationProvider).applyConfigurationToContainer(container, dockerContainer)
            }

            it("applies any configuration required for 'run as current user' mode before emitting the 'container created' event") {
                inOrder(runAsCurrentUserConfigurationProvider, eventSink) {
                    verify(runAsCurrentUserConfigurationProvider).applyConfigurationToContainer(container, dockerContainer)
                    verify(eventSink).postEvent(ContainerCreatedEvent(container, dockerContainer))
                }
            }
        }

        on("when creating the container fails") {
            beforeEachTestSuspend {
                whenever(dockerClient.createContainer(spec)).doThrow(ContainerCreationFailedException("Something went wrong."))

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

        on("when applying configuration for 'run as current user' mode fails") {
            beforeEachTest {
                whenever(runAsCurrentUserConfigurationProvider.applyConfigurationToContainer(any(), any())).doThrow(RunAsCurrentUserConfigurationException("Something went wrong."))

                runner.run(step, eventSink)
            }

            it("emits a 'container creation failed' event") {
                verify(eventSink).postEvent(ContainerCreationFailedEvent(container, "Something went wrong."))
            }

            it("still emits a 'container created' event") {
                verify(eventSink).postEvent(ContainerCreatedEvent(container, dockerContainer))
            }
        }

        on("when evaluating an expression for the container's configuration fails") {
            beforeEachTest {
                whenever(creationRequestFactory.create(any(), any(), any(), any(), anyOrNull(), anyOrNull(), any(), any()))
                    .doThrow(ExpressionEvaluationException("Something went wrong."))

                runner.run(step, eventSink)
            }

            it("emits a 'container creation failed' event") {
                verify(eventSink).postEvent(ContainerCreationFailedEvent(container, "Something went wrong."))
            }
        }
    }
})
