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
import batect.config.PortMapping
import batect.config.PullImage
import batect.config.ReferenceValue
import batect.config.VolumeMount
import batect.docker.ContainerCreationFailedException
import batect.docker.ContainerHealthCheckException
import batect.docker.ContainerRemovalFailedException
import batect.docker.ContainerStartFailedException
import batect.docker.ContainerStopFailedException
import batect.docker.DockerClient
import batect.docker.DockerContainer
import batect.docker.DockerContainerCreationRequest
import batect.docker.DockerContainerCreationRequestFactory
import batect.docker.DockerContainerRunResult
import batect.docker.DockerException
import batect.docker.DockerHealthCheckResult
import batect.docker.DockerImage
import batect.docker.DockerImageBuildProgress
import batect.docker.DockerNetwork
import batect.docker.HealthStatus
import batect.docker.ImageBuildFailedException
import batect.docker.ImagePullFailedException
import batect.docker.NetworkCreationFailedException
import batect.docker.NetworkDeletionFailedException
import batect.docker.UserAndGroup
import batect.docker.pull.DockerImagePullProgress
import batect.execution.CleanupOption
import batect.execution.RunAsCurrentUserConfiguration
import batect.execution.RunAsCurrentUserConfigurationProvider
import batect.execution.RunOptions
import batect.execution.model.events.ContainerBecameHealthyEvent
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerCreationFailedEvent
import batect.execution.model.events.ContainerDidNotBecomeHealthyEvent
import batect.execution.model.events.ContainerRemovalFailedEvent
import batect.execution.model.events.ContainerRemovedEvent
import batect.execution.model.events.ContainerRunFailedEvent
import batect.execution.model.events.ContainerStartFailedEvent
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
import batect.execution.model.events.TaskEventSink
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.events.TaskNetworkCreationFailedEvent
import batect.execution.model.events.TaskNetworkDeletedEvent
import batect.execution.model.events.TaskNetworkDeletionFailedEvent
import batect.execution.model.events.TemporaryDirectoryDeletedEvent
import batect.execution.model.events.TemporaryDirectoryDeletionFailedEvent
import batect.execution.model.events.TemporaryFileDeletedEvent
import batect.execution.model.events.TemporaryFileDeletionFailedEvent
import batect.logging.Logger
import batect.logging.Severity
import batect.os.Command
import batect.os.SystemInfo
import batect.os.proxies.ProxyEnvironmentVariablesProvider
import batect.testutils.InMemoryLogSink
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.hasMessage
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.testutils.withAdditionalData
import batect.testutils.withLogMessage
import batect.testutils.withSeverity
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files
import java.nio.file.Paths

object TaskStepRunnerSpec : Spek({
    describe("a task step runner") {
        val eventSink by createForEachTest { mock<TaskEventSink>() }
        val dockerClient by createForEachTest { mock<DockerClient>() }
        val creationRequestFactory by createForEachTest { mock<DockerContainerCreationRequestFactory>() }

        val proxyVariables = mapOf("SOME_PROXY_CONFIG" to "some_proxy", "SOME_OTHER_PROXY_CONFIG" to "some_other_value")
        val proxyEnvironmentVariablesProvider = mock<ProxyEnvironmentVariablesProvider> {
            on { getProxyEnvironmentVariables(emptySet()) } doReturn proxyVariables
        }

        val runAsCurrentUserConfiguration = RunAsCurrentUserConfiguration(
            setOf(VolumeMount("/tmp/local-path", "/tmp/remote-path", "rw")),
            UserAndGroup(456, 789)
        )

        val systemInfo = mock<SystemInfo> {
            on { lineSeparator } doReturn "SYSTEM_LINE_SEPARATOR"
        }

        val runAsCurrentUserConfigurationProvider = mock<RunAsCurrentUserConfigurationProvider> {
            on { generateConfiguration(any(), any()) } doReturn runAsCurrentUserConfiguration
        }

        val logSink = InMemoryLogSink()
        val runOptions = RunOptions("some-task", emptyList(), 123, CleanupOption.Cleanup, CleanupOption.Cleanup, true)
        val hostEnvironmentVariables = mapOf("SOME_ENV_VAR" to "some env var value")

        val logger = Logger("some.source", logSink)
        val runner by createForEachTest { TaskStepRunner(dockerClient, proxyEnvironmentVariablesProvider, creationRequestFactory, runAsCurrentUserConfigurationProvider, systemInfo, logger, hostEnvironmentVariables) }

        describe("running steps") {
            on("running any step") {
                val step = mock<TaskStep> {
                    on { toString() } doReturn "The step description"
                }

                beforeEachTest { runner.run(step, eventSink, runOptions) }

                it("logs that the step is starting") {
                    assertThat(
                        logSink, hasMessage(
                            withSeverity(Severity.Info) and
                                withLogMessage("Running step.") and
                                withAdditionalData("step", "The step description")
                        )
                    )
                }

                it("logs that the step has finished") {
                    assertThat(
                        logSink, hasMessage(
                            withSeverity(Severity.Info) and
                                withLogMessage("Step completed.") and
                                withAdditionalData("step", "The step description")
                        )
                    )
                }
            }

            describe("running a 'build image' step") {
                val buildDirectory = Paths.get("/some-build-dir")
                val buildArgs = mapOf("some_arg" to LiteralValue("some_value"), "SOME_PROXY_CONFIG" to LiteralValue("overridden"), "SOME_HOST_VAR" to ReferenceValue("SOME_ENV_VAR"))
                val dockerfilePath = "some-Dockerfile-path"
                val imageTags = setOf("some_image_tag", "some_other_image_tag")
                val imageSource = BuildImage(buildDirectory, buildArgs, dockerfilePath)
                val step = BuildImageStep(imageSource, imageTags)

                // TODO: handle case when env var referenced does not exist

                describe("when building the image succeeds") {
                    on("and propagating proxy-related environment variables is enabled") {
                        val image = DockerImage("some-image")
                        val update1 = DockerImageBuildProgress(1, 2, "First step", null)
                        val update2 = DockerImageBuildProgress(2, 2, "Second step", null)

                        beforeEachTest {
                            whenever(dockerClient.build(eq(buildDirectory), any(), eq(dockerfilePath), eq(imageTags), any()))
                                .then { invocation ->
                                    @Suppress("UNCHECKED_CAST")
                                    val onStatusUpdate = invocation.arguments[4] as (DockerImageBuildProgress) -> Unit

                                    onStatusUpdate(update1)
                                    onStatusUpdate(update2)

                                    image
                                }

                            runner.run(step, eventSink, runOptions)
                        }

                        it("passes the image build args provided by the user as well as any proxy-related build args, with user-provided build args overriding the generated proxy-related build args, and with any environment variable references resolved") {
                            val expectedArgs = mapOf(
                                "some_arg" to "some_value",
                                "SOME_PROXY_CONFIG" to "overridden",
                                "SOME_OTHER_PROXY_CONFIG" to "some_other_value",
                                "SOME_HOST_VAR" to "some env var value"
                            )

                            verify(dockerClient).build(any(), eq(expectedArgs), any(), any(), any())
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
                            whenever(dockerClient.build(eq(buildDirectory), any(), eq(dockerfilePath), eq(imageTags), any())).thenReturn(image)
                            runner.run(step, eventSink, runOptionsWithProxyEnvironmentVariablePropagationDisabled)
                        }

                        it("does not pass the proxy-related environment variables as image build arguments, but does still pass the user-provided build args with any environment variable references resolved") {
                            val expectedArgs = mapOf(
                                "some_arg" to "some_value",
                                "SOME_PROXY_CONFIG" to "overridden",
                                "SOME_HOST_VAR" to "some env var value"
                            )

                            verify(dockerClient).build(any(), eq(expectedArgs), any(), any(), any())
                        }

                        it("emits a 'image built' event") {
                            verify(eventSink).postEvent(ImageBuiltEvent(imageSource, image))
                        }
                    }
                }

                on("when building the image fails") {
                    beforeEachTest {
                        whenever(dockerClient.build(eq(buildDirectory), any(), eq(dockerfilePath), eq(imageTags), any())).thenThrow(ImageBuildFailedException("Something went wrong.\nMore details on this line."))
                        runner.run(step, eventSink, runOptions)
                    }

                    it("emits a 'image build failed' event with all line breaks replaced with the system line separator") {
                        verify(eventSink).postEvent(ImageBuildFailedEvent(imageSource, "Something went wrong.SYSTEM_LINE_SEPARATORMore details on this line."))
                    }
                }

                on("when a build arg refers to a host environment variable that does not exist") {
                    val imageSourceWithNonExistentHostVariable = BuildImage(buildDirectory, mapOf("SOME_HOST_VAR" to ReferenceValue("SOME_ENV_VAR_THAT_DOES_NOT_EXIST")), dockerfilePath)
                    val stepWithNonExistentHostVariable = BuildImageStep(imageSourceWithNonExistentHostVariable, imageTags)

                    beforeEachTest {
                        runner.run(stepWithNonExistentHostVariable, eventSink, runOptions)
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
                    val update1 = DockerImagePullProgress("Update 1", 10, 20)
                    val update2 = DockerImagePullProgress("Update 2", 15, 20)

                    beforeEachTest {
                        whenever(dockerClient.pullImage(eq("some-image"), any())).then { invocation ->
                            @Suppress("UNCHECKED_CAST")
                            val onStatusUpdate = invocation.arguments[1] as (DockerImagePullProgress) -> Unit

                            onStatusUpdate(update1)
                            onStatusUpdate(update2)

                            image
                        }

                        runner.run(step, eventSink, runOptions)
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
                        whenever(dockerClient.pullImage(eq("some-image"), any())).thenThrow(ImagePullFailedException("Pulling image 'some-image' failed. Output from Docker was: Something went wrong."))

                        runner.run(step, eventSink, runOptions)
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
                        whenever(dockerClient.createNewBridgeNetwork()).doReturn(network)

                        runner.run(step, eventSink, runOptions)
                    }

                    it("emits a 'network created' event") {
                        verify(eventSink).postEvent(TaskNetworkCreatedEvent(network))
                    }
                }

                on("when creating the network fails") {
                    beforeEachTest {
                        whenever(dockerClient.createNewBridgeNetwork()).doThrow(NetworkCreationFailedException("Something went wrong."))

                        runner.run(step, eventSink, runOptions)
                    }

                    it("emits a 'network creation failed' event") {
                        verify(eventSink).postEvent(TaskNetworkCreationFailedEvent("Something went wrong."))
                    }
                }
            }

            describe("running a 'create container' step") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val otherContainer = Container("some-other-container", imageSourceDoesNotMatter())
                val command = Command.parse("do-stuff")
                val entrypoint = Command.parse("sh")
                val workingDirectory = "some-dir"
                val additionalEnvironmentVariables = mapOf("SOME_VAR" to LiteralValue("some value"))
                val additionalPortMappings = setOf(PortMapping(123, 456))
                val image = DockerImage("some-image")
                val network = DockerNetwork("some-network")

                val step = CreateContainerStep(container, command, entrypoint, workingDirectory, additionalEnvironmentVariables, additionalPortMappings, setOf(container, otherContainer), image, network)
                val request = DockerContainerCreationRequest(image, network, command.parsedCommand, entrypoint.parsedCommand, "some-container", "some-container", emptyMap(), "/work-dir", emptySet(), emptySet(), HealthCheckConfig(), null, false, false, emptySet(), emptySet())

                beforeEachTest {
                    whenever(
                        creationRequestFactory.create(
                            container,
                            image,
                            network,
                            command,
                            entrypoint,
                            workingDirectory,
                            additionalEnvironmentVariables,
                            runAsCurrentUserConfiguration.volumeMounts,
                            additionalPortMappings,
                            runOptions.propagateProxyEnvironmentVariables,
                            runAsCurrentUserConfiguration.userAndGroup,
                            step.allContainersInNetwork
                        )
                    ).doReturn(request)
                }

                on("when creating the container succeeds") {
                    val dockerContainer = DockerContainer("some-id")

                    beforeEachTest {
                        whenever(dockerClient.create(request)).doReturn(dockerContainer)

                        runner.run(step, eventSink, runOptions)
                    }

                    it("creates the container with the provided configuration") {
                        verify(dockerClient).create(request)
                    }

                    it("emits a 'container created' event") {
                        verify(eventSink).postEvent(ContainerCreatedEvent(container, dockerContainer))
                    }
                }

                on("when creating the container fails") {
                    beforeEachTest {
                        whenever(dockerClient.create(request)).doThrow(ContainerCreationFailedException("Something went wrong."))

                        runner.run(step, eventSink, runOptions)
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

                on("when running the container succeeds") {
                    beforeEachTest {
                        whenever(dockerClient.run(dockerContainer)).doReturn(DockerContainerRunResult(123))

                        runner.run(step, eventSink, runOptions)
                    }

                    it("emits a 'running container exited' event") {
                        verify(eventSink).postEvent(RunningContainerExitedEvent(container, 123))
                    }
                }

                on("when running the container fails") {
                    beforeEachTest {
                        whenever(dockerClient.run(dockerContainer)).doThrow(DockerException("Something went wrong"))

                        runner.run(step, eventSink, runOptions)
                    }

                    it("emits a 'container run failed' event") {
                        verify(eventSink).postEvent(ContainerRunFailedEvent(container, "Something went wrong"))
                    }
                }
            }

            describe("running a 'start container' step") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val dockerContainer = DockerContainer("some-id")
                val step = StartContainerStep(container, dockerContainer)

                on("when starting the container succeeds") {
                    beforeEachTest { runner.run(step, eventSink, runOptions) }

                    it("starts the container") {
                        verify(dockerClient).start(dockerContainer)
                    }

                    it("emits a 'container started' event") {
                        verify(eventSink).postEvent(ContainerStartedEvent(container))
                    }
                }

                on("when starting the container fails") {
                    beforeEachTest {
                        whenever(dockerClient.start(dockerContainer)).thenThrow(ContainerStartFailedException("some-id", "Something went wrong"))

                        runner.run(step, eventSink, runOptions)
                    }

                    it("emits a 'container start failed' event") {
                        verify(eventSink).postEvent(ContainerStartFailedEvent(container, "Something went wrong"))
                    }
                }
            }

            describe("running a 'stop container' step") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val dockerContainer = DockerContainer("some-id")
                val step = StopContainerStep(container, dockerContainer)

                on("when stopping the container succeeds") {
                    beforeEachTest { runner.run(step, eventSink, runOptions) }

                    it("stops the container") {
                        verify(dockerClient).stop(dockerContainer)
                    }

                    it("emits a 'container stopped' event") {
                        verify(eventSink).postEvent(ContainerStoppedEvent(container))
                    }
                }

                on("when stopping the container fails") {
                    beforeEachTest {
                        whenever(dockerClient.stop(dockerContainer)).thenThrow(ContainerStopFailedException("some-id", "Something went wrong"))

                        runner.run(step, eventSink, runOptions)
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
                    beforeEachTest { runner.run(step, eventSink, runOptions) }

                    it("removes the container") {
                        verify(dockerClient).remove(dockerContainer)
                    }

                    it("emits a 'container removed' event") {
                        verify(eventSink).postEvent(ContainerRemovedEvent(container))
                    }
                }

                on("when removing the container fails") {
                    beforeEachTest {
                        whenever(dockerClient.remove(dockerContainer)).thenThrow(ContainerRemovalFailedException("some-id", "Something went wrong"))

                        runner.run(step, eventSink, runOptions)
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

                        runner.run(step, eventSink, runOptions)
                    }

                    it("emits a 'temporary file deleted' event") {
                        verify(eventSink).postEvent(TemporaryFileDeletedEvent(filePath))
                    }

                    it("deletes the file") {
                        assertThat(Files.exists(filePath), equalTo(false))
                    }
                }

                on("when deleting the file fails") {
                    beforeEachTest { runner.run(step, eventSink, runOptions) }

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

                        runner.run(step, eventSink, runOptions)
                    }

                    it("emits a 'temporary directory deleted' event") {
                        verify(eventSink).postEvent(TemporaryDirectoryDeletedEvent(directoryPath))
                    }

                    it("deletes the directory") {
                        assertThat(Files.exists(directoryPath), equalTo(false))
                    }
                }

                on("when deleting the directory fails") {
                    beforeEachTest { runner.run(step, eventSink, runOptions) }

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
                        whenever(dockerClient.waitForHealthStatus(dockerContainer)).doReturn(HealthStatus.NoHealthCheck)

                        runner.run(step, eventSink, runOptions)
                    }

                    it("emits a 'container became healthy' event") {
                        verify(eventSink).postEvent(ContainerBecameHealthyEvent(container))
                    }
                }

                on("when the container becomes healthy") {
                    beforeEachTest {
                        whenever(dockerClient.waitForHealthStatus(dockerContainer)).doReturn(HealthStatus.BecameHealthy)

                        runner.run(step, eventSink, runOptions)
                    }

                    it("emits a 'container became healthy' event") {
                        verify(eventSink).postEvent(ContainerBecameHealthyEvent(container))
                    }
                }

                describe("when the container becomes unhealthy") {
                    beforeEachTest {
                        whenever(dockerClient.waitForHealthStatus(dockerContainer)).doReturn(HealthStatus.BecameUnhealthy)
                    }

                    describe("when the last health check returned a non-zero exit code") {
                        on("and produced no output") {
                            beforeEachTest {
                                whenever(dockerClient.getLastHealthCheckResult(dockerContainer)).doReturn(DockerHealthCheckResult(2, ""))

                                runner.run(step, eventSink, runOptions)
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
                                whenever(dockerClient.getLastHealthCheckResult(dockerContainer)).doReturn(DockerHealthCheckResult(2, "Something's not ready yet.\nMore output on next line.\n"))

                                runner.run(step, eventSink, runOptions)
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
                            whenever(dockerClient.getLastHealthCheckResult(dockerContainer)).doReturn(DockerHealthCheckResult(0, ""))

                            runner.run(step, eventSink, runOptions)
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
                            whenever(dockerClient.getLastHealthCheckResult(dockerContainer)).doThrow(ContainerHealthCheckException("Something went wrong."))

                            runner.run(step, eventSink, runOptions)
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
                        whenever(dockerClient.waitForHealthStatus(dockerContainer)).doReturn(HealthStatus.Exited)

                        runner.run(step, eventSink, runOptions)
                    }

                    it("emits a 'container did not become healthy' event") {
                        verify(eventSink).postEvent(ContainerDidNotBecomeHealthyEvent(container, "The container exited before becoming healthy."))
                    }
                }

                on("when waiting for the container's health status fails") {
                    beforeEachTest {
                        whenever(dockerClient.waitForHealthStatus(dockerContainer)).doThrow(ContainerHealthCheckException("Something went wrong"))

                        runner.run(step, eventSink, runOptions)
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
                    beforeEachTest { runner.run(step, eventSink, runOptions) }

                    it("deletes the network") {
                        verify(dockerClient).deleteNetwork(network)
                    }

                    it("emits a 'network deleted' event") {
                        verify(eventSink).postEvent(TaskNetworkDeletedEvent)
                    }
                }

                on("when removing the container fails") {
                    beforeEachTest {
                        whenever(dockerClient.deleteNetwork(network)).thenThrow(NetworkDeletionFailedException("some-network", "Something went wrong"))

                        runner.run(step, eventSink, runOptions)
                    }

                    it("emits a 'network deletion failed' event") {
                        verify(eventSink).postEvent(TaskNetworkDeletionFailedEvent("Something went wrong"))
                    }
                }
            }
        }
    }
})
