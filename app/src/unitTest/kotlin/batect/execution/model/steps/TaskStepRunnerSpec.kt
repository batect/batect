/*
   Copyright 2017-2019 Charles Korn.

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

package batect.execution.model.steps

import batect.config.BuildImage
import batect.config.Container
import batect.config.HealthCheckConfig
import batect.config.LiteralValue
import batect.config.PullImage
import batect.config.ReferenceValue
import batect.config.VolumeMount
import batect.docker.ContainerCreationFailedException
import batect.docker.ContainerHealthCheckException
import batect.docker.client.DockerClient
import batect.docker.DockerContainer
import batect.docker.DockerContainerCreationRequest
import batect.docker.DockerContainerCreationRequestFactory
import batect.docker.DockerContainerEnvironmentVariableProvider
import batect.docker.DockerContainerRunResult
import batect.docker.DockerException
import batect.docker.DockerExecResult
import batect.docker.DockerHealthCheckResult
import batect.docker.DockerImage
import batect.docker.client.DockerImageBuildProgress
import batect.docker.DockerNetwork
import batect.docker.client.HealthStatus
import batect.docker.ImageBuildFailedException
import batect.docker.ImagePullFailedException
import batect.docker.UserAndGroup
import batect.docker.api.ContainerRemovalFailedException
import batect.docker.api.ContainerStopFailedException
import batect.docker.api.NetworkCreationFailedException
import batect.docker.api.NetworkDeletionFailedException
import batect.docker.client.DockerContainersClient
import batect.docker.client.DockerExecClient
import batect.docker.client.DockerImagesClient
import batect.docker.client.DockerNetworksClient
import batect.docker.client.DockerSystemInfoClient
import batect.docker.pull.DockerImageProgress
import batect.execution.CancellationContext
import batect.execution.CleanupOption
import batect.execution.ContainerRuntimeConfiguration
import batect.execution.RunAsCurrentUserConfiguration
import batect.execution.RunAsCurrentUserConfigurationProvider
import batect.execution.RunOptions
import batect.execution.TaskStepRunContext
import batect.execution.model.events.ContainerBecameHealthyEvent
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerCreationFailedEvent
import batect.execution.model.events.ContainerDidNotBecomeHealthyEvent
import batect.execution.model.events.ContainerBecameReadyEvent
import batect.execution.model.events.ContainerRemovalFailedEvent
import batect.execution.model.events.ContainerRemovedEvent
import batect.execution.model.events.ContainerRunFailedEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.ContainerStopFailedEvent
import batect.execution.model.events.ContainerStoppedEvent
import batect.execution.model.events.ImageBuildFailedEvent
import batect.execution.model.events.ImageBuildProgressEvent
import batect.execution.model.events.ImageBuiltEvent
import batect.execution.model.events.ImagePullFailedEvent
import batect.execution.model.events.ImagePullProgressEvent
import batect.execution.model.events.ImagePulledEvent
import batect.execution.model.events.RunningContainerExitedEvent
import batect.execution.model.events.RunningSetupCommandEvent
import batect.execution.model.events.SetupCommandExecutionErrorEvent
import batect.execution.model.events.SetupCommandFailedEvent
import batect.execution.model.events.SetupCommandsCompletedEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.events.TaskNetworkCreationFailedEvent
import batect.execution.model.events.TaskNetworkDeletedEvent
import batect.execution.model.events.TaskNetworkDeletionFailedEvent
import batect.execution.model.events.TemporaryDirectoryDeletedEvent
import batect.execution.model.events.TemporaryDirectoryDeletionFailedEvent
import batect.execution.model.events.TemporaryFileDeletedEvent
import batect.execution.model.events.TemporaryFileDeletionFailedEvent
import batect.os.Command
import batect.os.Dimensions
import batect.os.SystemInfo
import batect.os.proxies.ProxyEnvironmentVariablesProvider
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.ui.containerio.ContainerIOStreamingOptions
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import okio.Sink
import okio.Source
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files
import java.nio.file.Paths

object TaskStepRunnerSpec : Spek({
    describe("a task step runner") {
        val eventSink by createForEachTest { mock<TaskEventSink>() }
        val runOptions = RunOptions("some-task", emptyList(), CleanupOption.Cleanup, CleanupOption.Cleanup, true)
        val cancellationContext by createForEachTest { mock<CancellationContext>() }
        val ioStreamingOptions by createForEachTest { mock<ContainerIOStreamingOptions>() }
        val stepRunContext by createForEachTest { TaskStepRunContext(eventSink, runOptions, cancellationContext, ioStreamingOptions) }

        val containersClient by createForEachTest { mock<DockerContainersClient>() }
        val execClient by createForEachTest { mock<DockerExecClient>() }
        val imagesClient by createForEachTest { mock<DockerImagesClient>() }
        val networksClient by createForEachTest { mock<DockerNetworksClient>() }
        val systemInfoClient by createForEachTest { mock<DockerSystemInfoClient>() }
        val dockerClient by createForEachTest { DockerClient(containersClient, execClient, imagesClient, networksClient, systemInfoClient) }
        val creationRequestFactory by createForEachTest { mock<DockerContainerCreationRequestFactory>() }
        val environmentVariableProvider by createForEachTest { mock<DockerContainerEnvironmentVariableProvider>() }

        val proxyVariables = mapOf("SOME_PROXY_CONFIG" to "some_proxy", "SOME_OTHER_PROXY_CONFIG" to "some_other_value")
        val proxyEnvironmentVariablesProvider = mock<ProxyEnvironmentVariablesProvider> {
            on { getProxyEnvironmentVariables(emptySet()) } doReturn proxyVariables
        }

        val userAndGroup = UserAndGroup(456, 789)
        val runAsCurrentUserConfiguration = RunAsCurrentUserConfiguration(
            setOf(VolumeMount("/tmp/local-path", "/tmp/remote-path", "rw")),
            userAndGroup
        )

        val systemInfo = mock<SystemInfo> {
            on { lineSeparator } doReturn "SYSTEM_LINE_SEPARATOR"
        }

        val runAsCurrentUserConfigurationProvider = mock<RunAsCurrentUserConfigurationProvider> {
            on { generateConfiguration(any(), any()) } doReturn runAsCurrentUserConfiguration
            on { determineUserAndGroup(any()) } doReturn userAndGroup
        }

        val hostEnvironmentVariables = mapOf("SOME_ENV_VAR" to "some env var value")

        val runner by createForEachTest { TaskStepRunner(dockerClient, proxyEnvironmentVariablesProvider, creationRequestFactory, environmentVariableProvider, runAsCurrentUserConfigurationProvider, systemInfo, hostEnvironmentVariables) }

        describe("running steps") {
            describe("running a 'build image' step") {
                val buildDirectory = Paths.get("/some-build-dir")
                val buildArgs = mapOf("some_arg" to LiteralValue("some_value"), "SOME_PROXY_CONFIG" to LiteralValue("overridden"), "SOME_HOST_VAR" to ReferenceValue("SOME_ENV_VAR"))
                val dockerfilePath = "some-Dockerfile-path"
                val imageTags = setOf("some_image_tag", "some_other_image_tag")
                val imageSource = BuildImage(buildDirectory, buildArgs, dockerfilePath)
                val step = BuildImageStep(imageSource, imageTags)

                describe("when building the image succeeds") {
                    on("and propagating proxy-related environment variables is enabled") {
                        val image = DockerImage("some-image")
                        val update1 = DockerImageBuildProgress(1, 2, "First step", null)
                        val update2 = DockerImageBuildProgress(2, 2, "Second step", null)

                        beforeEachTest {
                            whenever(imagesClient.build(eq(buildDirectory), any(), eq(dockerfilePath), eq(imageTags), eq(cancellationContext), any()))
                                .then { invocation ->
                                    @Suppress("UNCHECKED_CAST")
                                    val onStatusUpdate = invocation.arguments[5] as (DockerImageBuildProgress) -> Unit

                                    onStatusUpdate(update1)
                                    onStatusUpdate(update2)

                                    image
                                }

                            runner.run(step, stepRunContext)
                        }

                        it("passes the image build args provided by the user as well as any proxy-related build args, with user-provided build args overriding the generated proxy-related build args, and with any environment variable references resolved") {
                            val expectedArgs = mapOf(
                                "some_arg" to "some_value",
                                "SOME_PROXY_CONFIG" to "overridden",
                                "SOME_OTHER_PROXY_CONFIG" to "some_other_value",
                                "SOME_HOST_VAR" to "some env var value"
                            )

                            verify(imagesClient).build(any(), eq(expectedArgs), any(), any(), any(), any())
                        }

                        it("emits a 'image build progress' event for each update received from Docker") {
                            verify(eventSink).postEvent(ImageBuildProgressEvent(imageSource, update1))
                            verify(eventSink).postEvent(ImageBuildProgressEvent(imageSource, update2))
                        }

                        it("emits a 'image built' event") {
                            verify(eventSink).postEvent(ImageBuiltEvent(imageSource, image))
                        }
                    }

                    on("and propagating proxy-related environment variables is disabled") {
                        val image = DockerImage("some-image")
                        val runOptionsWithProxyEnvironmentVariablePropagationDisabled = runOptions.copy(propagateProxyEnvironmentVariables = false)

                        beforeEachTest {
                            whenever(imagesClient.build(eq(buildDirectory), any(), eq(dockerfilePath), eq(imageTags), eq(cancellationContext), any())).thenReturn(image)
                            runner.run(step, stepRunContext.copy(runOptions = runOptionsWithProxyEnvironmentVariablePropagationDisabled))
                        }

                        it("does not pass the proxy-related environment variables as image build arguments, but does still pass the user-provided build args with any environment variable references resolved") {
                            val expectedArgs = mapOf(
                                "some_arg" to "some_value",
                                "SOME_PROXY_CONFIG" to "overridden",
                                "SOME_HOST_VAR" to "some env var value"
                            )

                            verify(imagesClient).build(any(), eq(expectedArgs), any(), any(), any(), any())
                        }

                        it("emits a 'image built' event") {
                            verify(eventSink).postEvent(ImageBuiltEvent(imageSource, image))
                        }
                    }
                }

                on("when building the image fails") {
                    beforeEachTest {
                        whenever(imagesClient.build(eq(buildDirectory), any(), eq(dockerfilePath), eq(imageTags), eq(cancellationContext), any())).thenThrow(ImageBuildFailedException("Something went wrong.\nMore details on this line."))
                        runner.run(step, stepRunContext)
                    }

                    it("emits a 'image build failed' event with all line breaks replaced with the system line separator") {
                        verify(eventSink).postEvent(ImageBuildFailedEvent(imageSource, "Something went wrong.SYSTEM_LINE_SEPARATORMore details on this line."))
                    }
                }

                on("when a build arg refers to a host environment variable that does not exist") {
                    val imageSourceWithNonExistentHostVariable = BuildImage(buildDirectory, mapOf("SOME_HOST_VAR" to ReferenceValue("SOME_ENV_VAR_THAT_DOES_NOT_EXIST")), dockerfilePath)
                    val stepWithNonExistentHostVariable = BuildImageStep(imageSourceWithNonExistentHostVariable, imageTags)

                    beforeEachTest {
                        runner.run(stepWithNonExistentHostVariable, stepRunContext)
                    }

                    it("emits a 'image build failed' event") {
                        verify(eventSink).postEvent(ImageBuildFailedEvent(imageSourceWithNonExistentHostVariable, "The value for the build arg 'SOME_HOST_VAR' cannot be evaluated: The host environment variable 'SOME_ENV_VAR_THAT_DOES_NOT_EXIST' is not set, and no default value has been provided."))
                    }
                }
            }

            describe("running a 'pull image' step") {
                val source = PullImage("some-image")
                val step = PullImageStep(source)

                on("when pulling the image succeeds") {
                    val image = DockerImage("some-image")
                    val update1 = DockerImageProgress("Update 1", 10, 20)
                    val update2 = DockerImageProgress("Update 2", 15, 20)

                    beforeEachTest {
                        whenever(imagesClient.pull(eq("some-image"), eq(cancellationContext), any())).then { invocation ->
                            @Suppress("UNCHECKED_CAST")
                            val onStatusUpdate = invocation.arguments[2] as (DockerImageProgress) -> Unit

                            onStatusUpdate(update1)
                            onStatusUpdate(update2)

                            image
                        }

                        runner.run(step, stepRunContext)
                    }

                    it("emits a 'image pulled' event") {
                        verify(eventSink).postEvent(ImagePulledEvent(source, image))
                    }

                    it("emits a 'image pull progress' event for each update received from Docker") {
                        verify(eventSink).postEvent(ImagePullProgressEvent(source, update1))
                        verify(eventSink).postEvent(ImagePullProgressEvent(source, update2))
                    }
                }

                on("when building the image fails") {
                    beforeEachTest {
                        whenever(imagesClient.pull(eq("some-image"), eq(cancellationContext), any())).thenThrow(ImagePullFailedException("Pulling image 'some-image' failed. Output from Docker was: Something went wrong."))

                        runner.run(step, stepRunContext)
                    }

                    it("emits a 'image pull failed' event") {
                        verify(eventSink).postEvent(ImagePullFailedEvent(source, "Pulling image 'some-image' failed. Output from Docker was: Something went wrong."))
                    }
                }
            }

            describe("running a 'create task network' step") {
                val step = CreateTaskNetworkStep

                on("when creating the network succeeds") {
                    val network = DockerNetwork("some-network")

                    beforeEachTest {
                        whenever(networksClient.create()).doReturn(network)

                        runner.run(step, stepRunContext)
                    }

                    it("emits a 'network created' event") {
                        verify(eventSink).postEvent(TaskNetworkCreatedEvent(network))
                    }
                }

                on("when creating the network fails") {
                    beforeEachTest {
                        whenever(networksClient.create()).doThrow(NetworkCreationFailedException("Something went wrong."))

                        runner.run(step, stepRunContext)
                    }

                    it("emits a 'network creation failed' event") {
                        verify(eventSink).postEvent(TaskNetworkCreationFailedEvent("Something went wrong."))
                    }
                }
            }

            describe("running a 'create container' step") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val otherContainer = Container("some-other-container", imageSourceDoesNotMatter())
                val image = DockerImage("some-image")
                val network = DockerNetwork("some-network")

                val config = mock<ContainerRuntimeConfiguration>()
                val step = CreateContainerStep(container, config, setOf(container, otherContainer), image, network)
                val request = DockerContainerCreationRequest(image, network, Command.parse("do-stuff").parsedCommand, Command.parse("sh").parsedCommand, "some-container", setOf("some-container"), emptyMap(), "/work-dir", emptySet(), emptySet(), HealthCheckConfig(), null, false, false, emptySet(), emptySet())

                beforeEachTest {
                    whenever(ioStreamingOptions.terminalTypeForContainer(container)).doReturn("some-terminal")

                    whenever(
                        creationRequestFactory.create(
                            container,
                            image,
                            network,
                            config,
                            runAsCurrentUserConfiguration.volumeMounts,
                            runOptions.propagateProxyEnvironmentVariables,
                            runAsCurrentUserConfiguration.userAndGroup,
                            "some-terminal",
                            step.allContainersInNetwork
                        )
                    ).doReturn(request)
                }

                on("when creating the container succeeds") {
                    val dockerContainer = DockerContainer("some-id")

                    beforeEachTest {
                        whenever(containersClient.create(request)).doReturn(dockerContainer)

                        runner.run(step, stepRunContext)
                    }

                    it("creates the container with the provided configuration") {
                        verify(containersClient).create(request)
                    }

                    it("emits a 'container created' event") {
                        verify(eventSink).postEvent(ContainerCreatedEvent(container, dockerContainer))
                    }
                }

                on("when creating the container fails") {
                    beforeEachTest {
                        whenever(containersClient.create(request)).doThrow(ContainerCreationFailedException("Something went wrong."))

                        runner.run(step, stepRunContext)
                    }

                    it("emits a 'container creation failed' event") {
                        verify(eventSink).postEvent(ContainerCreationFailedEvent(container, "Something went wrong."))
                    }
                }
            }

            describe("running a 'run container' step") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val dockerContainer = DockerContainer("some-id")
                val step = RunContainerStep(container, dockerContainer)

                val stdout = mock<Sink>()
                val stdin = mock<Source>()
                val frameDimensions = Dimensions(20, 30)

                beforeEachTest {
                    whenever(ioStreamingOptions.stdoutForContainer(container)).doReturn(stdout)
                    whenever(ioStreamingOptions.stdinForContainer(container)).doReturn(stdin)
                    whenever(ioStreamingOptions.frameDimensions).doReturn(frameDimensions)
                }

                on("when running the container succeeds") {
                    beforeEachTest {
                        whenever(containersClient.run(any(), any(), any(), any(), any(), any())).doAnswer { invocation ->
                            @Suppress("UNCHECKED_CAST")
                            val onStartedHandler = invocation.arguments[5] as () -> Unit

                            onStartedHandler()

                            DockerContainerRunResult(123)
                        }

                        runner.run(step, stepRunContext)
                    }

                    it("runs the container with the stdin and stdout provided by the I/O streaming options") {
                        verify(containersClient).run(eq(dockerContainer), eq(stdout), eq(stdin), eq(cancellationContext), eq(frameDimensions), any())
                    }

                    it("emits a 'container started' event") {
                        verify(eventSink).postEvent(ContainerStartedEvent(container))
                    }

                    it("emits a 'running container exited' event") {
                        verify(eventSink).postEvent(RunningContainerExitedEvent(container, 123))
                    }
                }

                on("when running the container fails") {
                    beforeEachTest {
                        whenever(containersClient.run(any(), any(), any(), any(), any(), any())).doThrow(DockerException("Something went wrong"))

                        runner.run(step, stepRunContext)
                    }

                    it("emits a 'container run failed' event") {
                        verify(eventSink).postEvent(ContainerRunFailedEvent(container, "Something went wrong"))
                    }
                }
            }

            describe("running a 'stop container' step") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val dockerContainer = DockerContainer("some-id")
                val step = StopContainerStep(container, dockerContainer)

                on("when stopping the container succeeds") {
                    beforeEachTest { runner.run(step, stepRunContext) }

                    it("stops the container") {
                        verify(containersClient).stop(dockerContainer)
                    }

                    it("emits a 'container stopped' event") {
                        verify(eventSink).postEvent(ContainerStoppedEvent(container))
                    }
                }

                on("when stopping the container fails") {
                    beforeEachTest {
                        whenever(containersClient.stop(dockerContainer)).thenThrow(ContainerStopFailedException("some-id", "Something went wrong"))

                        runner.run(step, stepRunContext)
                    }

                    it("emits a 'container stop failed' event") {
                        verify(eventSink).postEvent(ContainerStopFailedEvent(container, "Something went wrong"))
                    }
                }
            }

            describe("running a 'remove container' step") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val dockerContainer = DockerContainer("some-id")
                val step = RemoveContainerStep(container, dockerContainer)

                on("when removing the container succeeds") {
                    beforeEachTest { runner.run(step, stepRunContext) }

                    it("removes the container") {
                        verify(containersClient).remove(dockerContainer)
                    }

                    it("emits a 'container removed' event") {
                        verify(eventSink).postEvent(ContainerRemovedEvent(container))
                    }
                }

                on("when removing the container fails") {
                    beforeEachTest {
                        whenever(containersClient.remove(dockerContainer)).thenThrow(ContainerRemovalFailedException("some-id", "Something went wrong"))

                        runner.run(step, stepRunContext)
                    }

                    it("emits a 'container removal failed' event") {
                        verify(eventSink).postEvent(ContainerRemovalFailedEvent(container, "Something went wrong"))
                    }
                }
            }

            describe("running a 'delete temporary file' step") {
                val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
                val filePath by createForEachTest { fileSystem.getPath("/temp-file") }

                val step by createForEachTest { DeleteTemporaryFileStep(filePath) }

                on("when deleting the file succeeds") {
                    beforeEachTest {
                        Files.write(filePath, listOf("test file contents"))

                        runner.run(step, stepRunContext)
                    }

                    it("emits a 'temporary file deleted' event") {
                        verify(eventSink).postEvent(TemporaryFileDeletedEvent(filePath))
                    }

                    it("deletes the file") {
                        assertThat(Files.exists(filePath), equalTo(false))
                    }
                }

                on("when deleting the file fails") {
                    beforeEachTest { runner.run(step, stepRunContext) }

                    it("emits a 'temporary file deletion failed' event") {
                        verify(eventSink).postEvent(TemporaryFileDeletionFailedEvent(filePath, "java.nio.file.NoSuchFileException: /temp-file"))
                    }
                }
            }

            describe("running a 'delete temporary directory' step") {
                val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
                val directoryPath by createForEachTest { fileSystem.getPath("/temp-directory") }

                val step by createForEachTest { DeleteTemporaryDirectoryStep(directoryPath) }

                on("when deleting the directory succeeds") {
                    beforeEachTest {
                        Files.createDirectories(directoryPath)
                        Files.write(directoryPath.resolve("some-file"), listOf("some file content"))

                        runner.run(step, stepRunContext)
                    }

                    it("emits a 'temporary directory deleted' event") {
                        verify(eventSink).postEvent(TemporaryDirectoryDeletedEvent(directoryPath))
                    }

                    it("deletes the directory") {
                        assertThat(Files.exists(directoryPath), equalTo(false))
                    }
                }

                on("when deleting the directory fails") {
                    beforeEachTest { runner.run(step, stepRunContext) }

                    it("emits a 'temporary directory deletion failed' event") {
                        verify(eventSink).postEvent(TemporaryDirectoryDeletionFailedEvent(directoryPath, "java.nio.file.NoSuchFileException: /temp-directory"))
                    }
                }
            }

            describe("running a 'wait for container to become healthy' step") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val dockerContainer = DockerContainer("some-id")
                val step = WaitForContainerToBecomeHealthyStep(container, dockerContainer)

                on("when the container has no health check") {
                    beforeEachTest {
                        whenever(containersClient.waitForHealthStatus(dockerContainer, cancellationContext)).doReturn(HealthStatus.NoHealthCheck)

                        runner.run(step, stepRunContext)
                    }

                    it("emits a 'container became healthy' event") {
                        verify(eventSink).postEvent(ContainerBecameHealthyEvent(container))
                    }
                }

                on("when the container becomes healthy") {
                    beforeEachTest {
                        whenever(containersClient.waitForHealthStatus(dockerContainer, cancellationContext)).doReturn(HealthStatus.BecameHealthy)

                        runner.run(step, stepRunContext)
                    }

                    it("emits a 'container became healthy' event") {
                        verify(eventSink).postEvent(ContainerBecameHealthyEvent(container))
                    }
                }

                describe("when the container becomes unhealthy") {
                    beforeEachTest {
                        whenever(containersClient.waitForHealthStatus(dockerContainer, cancellationContext)).doReturn(HealthStatus.BecameUnhealthy)
                    }

                    describe("when the last health check returned a non-zero exit code") {
                        on("and produced no output") {
                            beforeEachTest {
                                whenever(containersClient.getLastHealthCheckResult(dockerContainer)).doReturn(DockerHealthCheckResult(2, ""))

                                runner.run(step, stepRunContext)
                            }

                            it("emits a 'container did not become healthy' event with details of the last health check") {
                                verify(eventSink).postEvent(
                                    ContainerDidNotBecomeHealthyEvent(
                                        container,
                                        "The configured health check did not indicate that the container was healthy within the timeout period. The last health check exited with code 2 but did not produce any output."
                                    )
                                )
                            }
                        }

                        on("and produced some output") {
                            beforeEachTest {
                                whenever(containersClient.getLastHealthCheckResult(dockerContainer)).doReturn(DockerHealthCheckResult(2, "Something's not ready yet.\nMore output on next line.\n"))

                                runner.run(step, stepRunContext)
                            }

                            it("emits a 'container did not become healthy' event with details of the last health check with all line breaks replaced with the system line separator") {
                                verify(eventSink).postEvent(
                                    ContainerDidNotBecomeHealthyEvent(
                                        container,
                                        "The configured health check did not indicate that the container was healthy within the timeout period. The last health check exited with code 2 and output:SYSTEM_LINE_SEPARATORSomething's not ready yet.SYSTEM_LINE_SEPARATORMore output on next line."
                                    )
                                )
                            }
                        }
                    }

                    on("when the last health check returned a zero exit code") {
                        beforeEachTest {
                            whenever(containersClient.getLastHealthCheckResult(dockerContainer)).doReturn(DockerHealthCheckResult(0, ""))

                            runner.run(step, stepRunContext)
                        }

                        it("emits a 'container did not become healthy' event with an explanation of the race condition") {
                            verify(eventSink).postEvent(
                                ContainerDidNotBecomeHealthyEvent(
                                    container,
                                    "The configured health check did not indicate that the container was healthy within the timeout period. The most recent health check exited with code 0, which usually indicates that the container became healthy just after the timeout period expired."
                                )
                            )
                        }
                    }

                    on("when getting the last health check result throws an exception") {
                        beforeEachTest {
                            whenever(containersClient.getLastHealthCheckResult(dockerContainer)).doThrow(ContainerHealthCheckException("Something went wrong."))

                            runner.run(step, stepRunContext)
                        }

                        it("emits a 'container did not become healthy' event with details of the last health check") {
                            verify(eventSink).postEvent(
                                ContainerDidNotBecomeHealthyEvent(
                                    container,
                                    "Waiting for the container's health status failed: Something went wrong."
                                )
                            )
                        }
                    }
                }

                on("when the container exits before reporting a health status") {
                    beforeEachTest {
                        whenever(containersClient.waitForHealthStatus(dockerContainer, cancellationContext)).doReturn(HealthStatus.Exited)

                        runner.run(step, stepRunContext)
                    }

                    it("emits a 'container did not become healthy' event") {
                        verify(eventSink).postEvent(ContainerDidNotBecomeHealthyEvent(container, "The container exited before becoming healthy."))
                    }
                }

                on("when waiting for the container's health status fails") {
                    beforeEachTest {
                        whenever(containersClient.waitForHealthStatus(dockerContainer, cancellationContext)).doThrow(ContainerHealthCheckException("Something went wrong"))

                        runner.run(step, stepRunContext)
                    }

                    it("emits a 'container did not become healthy' event") {
                        verify(eventSink).postEvent(ContainerDidNotBecomeHealthyEvent(container, "Waiting for the container's health status failed: Something went wrong"))
                    }
                }
            }

            describe("running a 'delete task network' step") {
                val network = DockerNetwork("some-network")
                val step = DeleteTaskNetworkStep(network)

                on("when deleting the network succeeds") {
                    beforeEachTest { runner.run(step, stepRunContext) }

                    it("deletes the network") {
                        verify(networksClient).delete(network)
                    }

                    it("emits a 'network deleted' event") {
                        verify(eventSink).postEvent(TaskNetworkDeletedEvent)
                    }
                }

                on("when removing the container fails") {
                    beforeEachTest {
                        whenever(networksClient.delete(network)).thenThrow(NetworkDeletionFailedException("some-network", "Something went wrong"))

                        runner.run(step, stepRunContext)
                    }

                    it("emits a 'network deletion failed' event") {
                        verify(eventSink).postEvent(TaskNetworkDeletionFailedEvent("Something went wrong"))
                    }
                }
            }

            describe("running a 'run setup commands' step") {
                given("the container has no setup commands") {
                    val container = Container("the-container", imageSourceDoesNotMatter(), setupCommands = emptyList())
                    val config = ContainerRuntimeConfiguration(null, null, null, emptyMap(), emptySet())
                    val allContainersInNetwork = setOf(container)
                    val dockerContainer = DockerContainer("some-container-id")
                    val step = RunContainerSetupCommandsStep(container, config, allContainersInNetwork, dockerContainer)

                    beforeEachTest { runner.run(step, stepRunContext) }

                    it("emits a 'container ready' event") {
                        verify(eventSink).postEvent(ContainerBecameReadyEvent(container))
                    }

                    it("does not emit any other events") {
                        verify(eventSink, times(1)).postEvent(any())
                    }
                }

                given("the container has a single setup command") {
                    val command = Command.parse("./do the-thing")
                    val container = Container("the-container", imageSourceDoesNotMatter(), setupCommands = listOf(command), workingDirectory = "/some/work/dir")
                    val config = ContainerRuntimeConfiguration(null, null, null, emptyMap(), emptySet())
                    val allContainersInNetwork = setOf(container)
                    val dockerContainer = DockerContainer("some-container-id")
                    val step = RunContainerSetupCommandsStep(container, config, allContainersInNetwork, dockerContainer)

                    val environmentVariablesToUse = mapOf("SOME_VAR" to "some value")

                    beforeEachTest {
                        whenever(environmentVariableProvider.environmentVariablesFor(container, config, runOptions.propagateProxyEnvironmentVariables, null, allContainersInNetwork)).doReturn(environmentVariablesToUse)
                    }

                    given("the command succeeds") {
                        beforeEachTest {
                            whenever(execClient.run(any(), any(), any(), any(), any(), any(), any())).doReturn(DockerExecResult(0, "Some output from the command"))
                        }

                        beforeEachTest { runner.run(step, stepRunContext) }

                        it("emits a 'running setup command' event") {
                            verify(eventSink).postEvent(RunningSetupCommandEvent(container, command, 0))
                        }

                        it("runs the command") {
                            verify(execClient).run(
                                command,
                                dockerContainer,
                                environmentVariablesToUse,
                                container.privileged,
                                userAndGroup,
                                container.workingDirectory,
                                cancellationContext
                            )
                        }

                        it("emits a 'setup commands completed' event") {
                            verify(eventSink).postEvent(SetupCommandsCompletedEvent(container))
                        }

                        it("emits a 'container ready' event") {
                            verify(eventSink).postEvent(ContainerBecameReadyEvent(container))
                        }

                        it("emits the 'running setup command' event before running the command") {
                            inOrder(eventSink, execClient) {
                                verify(eventSink).postEvent(isA<RunningSetupCommandEvent>())
                                verify(execClient).run(any(), any(), any(), any(), any(), any(), any())
                            }
                        }

                        it("emits the 'setup commands completed' event after running the command") {
                            inOrder(eventSink, execClient) {
                                verify(execClient).run(any(), any(), any(), any(), any(), any(), any())
                                verify(eventSink).postEvent(isA<SetupCommandsCompletedEvent>())
                            }
                        }
                    }

                    given("the command returns a non-zero exit code") {
                        beforeEachTest {
                            whenever(execClient.run(any(), any(), any(), any(), any(), any(), any())).doReturn(DockerExecResult(123, "Some output from the command"))
                        }

                        beforeEachTest { runner.run(step, stepRunContext) }

                        it("emits a 'running setup command' event") {
                            verify(eventSink).postEvent(RunningSetupCommandEvent(container, command, 0))
                        }

                        it("emits a 'setup command failed' event") {
                            verify(eventSink).postEvent(SetupCommandFailedEvent(container, command, 123, "Some output from the command"))
                        }

                        it("does not emit a 'setup commands completed' event") {
                            verify(eventSink, never()).postEvent(isA<SetupCommandsCompletedEvent>())
                        }

                        it("does not emit a 'container ready' event") {
                            verify(eventSink, never()).postEvent(isA<ContainerBecameReadyEvent>())
                        }
                    }

                    given("the command cannot be run") {
                        beforeEachTest {
                            whenever(execClient.run(any(), any(), any(), any(), any(), any(), any())).doThrow(DockerException("Something went wrong."))
                        }

                        beforeEachTest { runner.run(step, stepRunContext) }

                        it("emits a 'running setup command' event") {
                            verify(eventSink).postEvent(RunningSetupCommandEvent(container, command, 0))
                        }

                        it("emits a 'setup command execution error' event") {
                            verify(eventSink).postEvent(SetupCommandExecutionErrorEvent(container, command, "Something went wrong."))
                        }

                        it("does not emit a 'setup commands completed' event") {
                            verify(eventSink, never()).postEvent(isA<SetupCommandsCompletedEvent>())
                        }

                        it("does not emit a 'container ready' event") {
                            verify(eventSink, never()).postEvent(isA<ContainerBecameReadyEvent>())
                        }
                    }
                }

                given("the container has multiple setup commands") {
                    val command1 = Command.parse("./do the-first-thing")
                    val command2 = Command.parse("./do the-second-thing")
                    val command3 = Command.parse("./do the-third-thing")
                    val container = Container("the-container", imageSourceDoesNotMatter(), setupCommands = listOf(command1, command2, command3), workingDirectory = "/some/work/dir")
                    val config = ContainerRuntimeConfiguration(null, null, null, emptyMap(), emptySet())
                    val allContainersInNetwork = setOf(container)
                    val dockerContainer = DockerContainer("some-container-id")
                    val step = RunContainerSetupCommandsStep(container, config, allContainersInNetwork, dockerContainer)

                    val environmentVariablesToUse = mapOf("SOME_VAR" to "some value")

                    beforeEachTest {
                        whenever(environmentVariableProvider.environmentVariablesFor(container, config, runOptions.propagateProxyEnvironmentVariables, null, allContainersInNetwork)).doReturn(environmentVariablesToUse)
                    }

                    given("all of them succeed") {
                        beforeEachTest {
                            whenever(execClient.run(any(), any(), any(), any(), any(), any(), any())).doReturn(DockerExecResult(0, "Some output from the command"))
                        }

                        beforeEachTest { runner.run(step, stepRunContext) }

                        it("emits a 'running setup command' event before running each command in the order provided") {
                            inOrder(eventSink, execClient) {
                                verify(eventSink).postEvent(RunningSetupCommandEvent(container, command1, 0))
                                verify(execClient).run(command1, dockerContainer, environmentVariablesToUse, container.privileged, userAndGroup, container.workingDirectory, cancellationContext)
                                verify(eventSink).postEvent(RunningSetupCommandEvent(container, command2, 1))
                                verify(execClient).run(command2, dockerContainer, environmentVariablesToUse, container.privileged, userAndGroup, container.workingDirectory, cancellationContext)
                                verify(eventSink).postEvent(RunningSetupCommandEvent(container, command3, 2))
                                verify(execClient).run(command3, dockerContainer, environmentVariablesToUse, container.privileged, userAndGroup, container.workingDirectory, cancellationContext)
                            }
                        }

                        it("emits 'setup commands completed' and 'container ready' events after running the last command") {
                            inOrder(eventSink, execClient) {
                                verify(execClient).run(command3, dockerContainer, environmentVariablesToUse, container.privileged, userAndGroup, container.workingDirectory, cancellationContext)
                                verify(eventSink).postEvent(SetupCommandsCompletedEvent(container))
                                verify(eventSink).postEvent(ContainerBecameReadyEvent(container))
                            }
                        }
                    }

                    given("one of them fails") {
                        beforeEachTest {
                            whenever(execClient.run(eq(command1), any(), any(), any(), any(), any(), any())).doReturn(DockerExecResult(0, "Some output from the command"))
                            whenever(execClient.run(eq(command3), any(), any(), any(), any(), any(), any())).doReturn(DockerExecResult(0, "Some output from the command"))
                        }

                        given("one of them fails with a non-zero exit code") {
                            beforeEachTest {
                                whenever(execClient.run(eq(command2), any(), any(), any(), any(), any(), any())).doReturn(DockerExecResult(123, "Some output from the failing command"))
                            }

                            beforeEachTest { runner.run(step, stepRunContext) }

                            it("emits a 'running setup command' event for the commands before the failing command") {
                                verify(eventSink).postEvent(RunningSetupCommandEvent(container, command1, 0))
                            }

                            it("emits a 'running setup command' event for the failing command") {
                                verify(eventSink).postEvent(RunningSetupCommandEvent(container, command2, 1))
                            }

                            it("emits a 'setup command failed' event for the failing command") {
                                verify(eventSink).postEvent(SetupCommandFailedEvent(container, command2, 123, "Some output from the failing command"))
                            }

                            it("does not emit a 'setup commands completed' event") {
                                verify(eventSink, never()).postEvent(isA<SetupCommandsCompletedEvent>())
                            }

                            it("does not emit a 'container ready' event") {
                                verify(eventSink, never()).postEvent(isA<ContainerBecameReadyEvent>())
                            }

                            it("does not run subsequent commands") {
                                verify(execClient, never()).run(command3, dockerContainer, environmentVariablesToUse, container.privileged, userAndGroup, container.workingDirectory, cancellationContext)
                            }
                        }

                        given("one of them fails because the command cannot be run") {
                            beforeEachTest {
                                whenever(execClient.run(eq(command2), any(), any(), any(), any(), any(), any())).doThrow(DockerException("Something went wrong."))
                            }

                            beforeEachTest { runner.run(step, stepRunContext) }

                            it("emits a 'running setup command' event for the commands before the failing command") {
                                verify(eventSink).postEvent(RunningSetupCommandEvent(container, command1, 0))
                            }

                            it("emits a 'running setup command' event for the failing command") {
                                verify(eventSink).postEvent(RunningSetupCommandEvent(container, command2, 1))
                            }

                            it("emits a 'setup command execution error' event") {
                                verify(eventSink).postEvent(SetupCommandExecutionErrorEvent(container, command2, "Something went wrong."))
                            }

                            it("does not emit a 'setup commands completed' event") {
                                verify(eventSink, never()).postEvent(isA<SetupCommandsCompletedEvent>())
                            }

                            it("does not emit a 'container ready' event") {
                                verify(eventSink, never()).postEvent(isA<ContainerBecameReadyEvent>())
                            }

                            it("does not run subsequent commands") {
                                verify(execClient, never()).run(command3, dockerContainer, environmentVariablesToUse, container.privileged, userAndGroup, container.workingDirectory, cancellationContext)
                            }
                        }
                    }
                }
            }
        }
    }
})
