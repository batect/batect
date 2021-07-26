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

package batect.docker.client

import batect.docker.ContainerCreationRequest
import batect.docker.ContainerDirectory
import batect.docker.ContainerFile
import batect.docker.ContainerHealthCheckException
import batect.docker.DockerContainer
import batect.docker.DockerContainerConfiguration
import batect.docker.DockerContainerHealthCheckConfig
import batect.docker.DockerContainerHealthCheckState
import batect.docker.DockerContainerInfo
import batect.docker.DockerContainerState
import batect.docker.DockerEvent
import batect.docker.DockerException
import batect.docker.DockerHealthCheckResult
import batect.docker.api.ContainerInspectionFailedException
import batect.docker.api.ContainersAPI
import batect.docker.run.ContainerIOStreamer
import batect.docker.run.ContainerInputStream
import batect.docker.run.ContainerOutputStream
import batect.docker.run.ContainerTTYManager
import batect.docker.run.ContainerWaiter
import batect.docker.run.InputConnection
import batect.docker.run.OutputConnection
import batect.os.ConsoleManager
import batect.os.Dimensions
import batect.primitives.CancellationCallback
import batect.primitives.CancellationContext
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import okio.Sink
import okio.Source
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration
import java.util.concurrent.CompletableFuture

object ContainersClientSpec : Spek({
    describe("a Docker containers client") {
        val api by createForEachTest { mock<ContainersAPI>() }
        val consoleManager by createForEachTest { mock<ConsoleManager>() }
        val waiter by createForEachTest { mock<ContainerWaiter>() }
        val ioStreamer by createForEachTest { mock<ContainerIOStreamer>() }
        val ttyManager by createForEachTest { mock<ContainerTTYManager>() }
        val logger by createLoggerForEachTestWithoutCustomSerializers()
        val client by createForEachTest { ContainersClient(api, consoleManager, waiter, ioStreamer, ttyManager, logger) }

        describe("creating a container") {
            given("a container configuration and a built image") {
                val request = mock<ContainerCreationRequest>()

                on("creating the container") {
                    beforeEachTest { whenever(api.create(request)).doReturn(DockerContainer("abc123")) }

                    val result by runForEachTest { client.create(request) }

                    it("sends a request to the Docker daemon to create the container") {
                        verify(api).create(request)
                    }

                    it("returns the ID of the created container") {
                        assertThat(result.id, equalTo("abc123"))
                    }
                }
            }
        }

        describe("running a container") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id")
                val outputStream by createForEachTest { mock<ContainerOutputStream>() }
                val inputStream by createForEachTest { mock<ContainerInputStream>() }
                val frameDimensions = Dimensions(10, 20)
                val terminalRestorer by createForEachTest { mock<AutoCloseable>() }
                val resizingRestorer by createForEachTest { mock<AutoCloseable>() }
                val cancellationContext by createForEachTest { mock<CancellationContext>() }

                val onStartedHandler by createForEachTest { mock<() -> Unit>() }

                beforeEachTest {
                    whenever(waiter.startWaitingForContainerToExit(eq(container), any())).doReturn(CompletableFuture.completedFuture(123L))
                    whenever(api.attachToOutput(eq(container), any())).doReturn(outputStream)
                    whenever(api.attachToInput(container)).doReturn(inputStream)
                    whenever(consoleManager.enterRawMode()).doReturn(terminalRestorer)
                    whenever(ttyManager.monitorForSizeChanges(container, frameDimensions)).doReturn(resizingRestorer)
                }

                given("stdout is connected") {
                    val stdout by createForEachTest { mock<Sink>() }

                    given("stdin is connected") {
                        val stdin by createForEachTest { mock<Source>() }

                        given("a TTY is being used") {
                            val useTTY = true

                            on("running the container") {
                                val result by runForEachTest { client.run(container, stdout, stdin, useTTY, cancellationContext, frameDimensions, onStartedHandler) }

                                it("returns the exit code from the container") {
                                    assertThat(result.exitCode, equalTo(123))
                                }

                                it("starts waiting for the container to exit before starting the container") {
                                    inOrder(api, waiter) {
                                        verify(waiter).startWaitingForContainerToExit(container, cancellationContext)
                                        verify(api).start(container)
                                    }
                                }

                                it("starts streaming I/O after putting the terminal into raw mode and starting the container") {
                                    inOrder(api, consoleManager, ioStreamer) {
                                        verify(api).start(container)
                                        verify(consoleManager).enterRawMode()
                                        verify(ioStreamer).stream(OutputConnection.Connected(outputStream, stdout), InputConnection.Connected(stdin, inputStream), cancellationContext)
                                    }
                                }

                                it("starts monitoring for terminal size changes after starting the container but before streaming I/O") {
                                    inOrder(api, ttyManager, ioStreamer) {
                                        verify(api).start(container)
                                        verify(ttyManager).monitorForSizeChanges(container, frameDimensions)
                                        verify(ioStreamer).stream(OutputConnection.Connected(outputStream, stdout), InputConnection.Connected(stdin, inputStream), cancellationContext)
                                    }
                                }

                                it("stops monitoring for terminal size changes after the streaming completes") {
                                    inOrder(ioStreamer, resizingRestorer) {
                                        verify(ioStreamer).stream(OutputConnection.Connected(outputStream, stdout), InputConnection.Connected(stdin, inputStream), cancellationContext)
                                        verify(resizingRestorer).close()
                                    }
                                }

                                it("restores the terminal after streaming completes") {
                                    inOrder(ioStreamer, terminalRestorer) {
                                        verify(ioStreamer).stream(OutputConnection.Connected(outputStream, stdout), InputConnection.Connected(stdin, inputStream), cancellationContext)
                                        verify(terminalRestorer).close()
                                    }
                                }

                                it("attaches to the container output before starting the container") {
                                    inOrder(api) {
                                        verify(api).attachToOutput(container, useTTY)
                                        verify(api).start(container)
                                    }
                                }

                                it("attaches to the container input before starting the container") {
                                    inOrder(api) {
                                        verify(api).attachToInput(container)
                                        verify(api).start(container)
                                    }
                                }

                                it("notifies the caller that the container has started after starting the container but before streaming I/O") {
                                    inOrder(api, onStartedHandler, ioStreamer) {
                                        verify(api).start(container)
                                        verify(onStartedHandler).invoke()
                                        verify(ioStreamer).stream(any(), any(), any())
                                    }
                                }

                                it("closes the output stream after streaming the output completes") {
                                    inOrder(ioStreamer, outputStream) {
                                        verify(ioStreamer).stream(OutputConnection.Connected(outputStream, stdout), InputConnection.Connected(stdin, inputStream), cancellationContext)
                                        verify(outputStream).close()
                                    }
                                }

                                it("closes the input stream after streaming the output completes") {
                                    inOrder(ioStreamer, inputStream) {
                                        verify(ioStreamer).stream(OutputConnection.Connected(outputStream, stdout), InputConnection.Connected(stdin, inputStream), cancellationContext)
                                        verify(inputStream).close()
                                    }
                                }

                                it("does not stop the container") {
                                    verify(api, never()).stop(any())
                                }
                            }

                            on("cancelling the run") {
                                val cancellationContextThatImmediatelyCancels by createForEachTest {
                                    mock<CancellationContext> {
                                        on { addCancellationCallback(any()) } doAnswer { invocation ->
                                            @Suppress("UNCHECKED_CAST")
                                            val callback = invocation.arguments[0] as CancellationCallback

                                            callback()

                                            AutoCloseable { }
                                        }
                                    }
                                }

                                runForEachTest { client.run(container, stdout, stdin, useTTY, cancellationContextThatImmediatelyCancels, frameDimensions, onStartedHandler) }

                                it("stops the container") {
                                    verify(api).stop(container)
                                }
                            }
                        }

                        given("a TTY is not being used") {
                            val useTTY = false

                            on("running the container") {
                                val result by runForEachTest { client.run(container, stdout, stdin, useTTY, cancellationContext, frameDimensions, onStartedHandler) }

                                it("returns the exit code from the container") {
                                    assertThat(result.exitCode, equalTo(123))
                                }

                                it("starts the container") {
                                    verify(api).start(container)
                                }

                                it("streams the container output but not the input") {
                                    verify(ioStreamer).stream(OutputConnection.Connected(outputStream, stdout), InputConnection.Connected(stdin, inputStream), cancellationContext)
                                }

                                it("attaches to the container output") {
                                    verify(api).attachToOutput(container, useTTY)
                                }

                                it("attaches to the container input") {
                                    verify(api).attachToInput(container)
                                }

                                it("does not enter raw mode") {
                                    verify(consoleManager, never()).enterRawMode()
                                }

                                it("starts monitoring for console size changes") {
                                    verify(ttyManager).monitorForSizeChanges(container, frameDimensions)
                                }
                            }
                        }
                    }

                    given("stdin is not connected") {
                        val useTTY = true
                        val stdin: Source? = null

                        on("running the container") {
                            val result by runForEachTest { client.run(container, stdout, stdin, useTTY, cancellationContext, frameDimensions, onStartedHandler) }

                            it("returns the exit code from the container") {
                                assertThat(result.exitCode, equalTo(123))
                            }

                            it("starts the container") {
                                verify(api).start(container)
                            }

                            it("streams the container output but not the input") {
                                verify(ioStreamer).stream(OutputConnection.Connected(outputStream, stdout), InputConnection.Disconnected, cancellationContext)
                            }

                            it("attaches to the container output") {
                                verify(api).attachToOutput(container, useTTY)
                            }

                            it("does not attach to the container input") {
                                verify(api, never()).attachToInput(container)
                            }

                            it("does not enter raw mode") {
                                verify(consoleManager, never()).enterRawMode()
                            }

                            it("starts monitoring for console size changes") {
                                verify(ttyManager).monitorForSizeChanges(container, frameDimensions)
                            }
                        }
                    }
                }

                given("stdout is not connected") {
                    val useTTY = true
                    val stdout: Sink? = null

                    given("stdin is connected") {
                        val stdin by createForEachTest { mock<Source>() }

                        it("throws an appropriate exception") {
                            assertThat({ client.run(container, stdout, stdin, useTTY, cancellationContext, frameDimensions, onStartedHandler) }, throws<DockerException>(withMessage("Attempted to stream input to container without streaming container output.")))
                        }
                    }

                    given("stdin is not connected") {
                        val stdin: Source? = null

                        on("running the container") {
                            val result by runForEachTest { client.run(container, stdout, stdin, useTTY, cancellationContext, frameDimensions, onStartedHandler) }

                            it("returns the exit code from the container") {
                                assertThat(result.exitCode, equalTo(123))
                            }

                            it("starts the container") {
                                verify(api).start(container)
                            }

                            it("streams neither the container output nor the input") {
                                verify(ioStreamer).stream(OutputConnection.Disconnected, InputConnection.Disconnected, cancellationContext)
                            }

                            it("does not attach to the container output") {
                                verify(api, never()).attachToOutput(container, useTTY)
                            }

                            it("does not attach to the container input") {
                                verify(api, never()).attachToInput(container)
                            }

                            it("does not enter raw mode") {
                                verify(consoleManager, never()).enterRawMode()
                            }

                            it("starts monitoring for console size changes") {
                                verify(ttyManager).monitorForSizeChanges(container, frameDimensions)
                            }
                        }
                    }
                }
            }
        }

        describe("stopping a container") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id")

                on("stopping that container") {
                    beforeEachTest { client.stop(container) }

                    it("sends a request to the Docker daemon to stop the container") {
                        verify(api).stop(container)
                    }
                }
            }
        }

        describe("removing a container") {
            given("an existing container") {
                val container = DockerContainer("the-container-id")

                on("removing that container") {
                    beforeEachTest { client.remove(container) }

                    it("sends a request to the Docker daemon to remove the container") {
                        verify(api).remove(container)
                    }
                }
            }
        }

        describe("uploading files or directories to a container") {
            given("an existing container, a set of files and directories to upload and a destination within the container") {
                val container = DockerContainer("the-container-id")

                val itemsToUpload = setOf(
                    ContainerFile("file-1", 100, 200, "file contents".toByteArray(Charsets.UTF_8)),
                    ContainerDirectory("some-dir", 100, 200)
                )

                val destination = "/some-dir"

                on("uploading to that container") {
                    beforeEachTest { client.upload(container, itemsToUpload, destination) }

                    it("sends a request to the Docker daemon to remove the container") {
                        verify(api).upload(container, itemsToUpload, destination)
                    }
                }
            }
        }

        describe("waiting for a container to report its health status") {
            val cancellationContext by createForEachTest { mock<CancellationContext>() }

            given("a Docker container with no health check") {
                val container = DockerContainer("the-container-id")

                beforeEachTest {
                    whenever(api.inspect(container)).thenReturn(
                        DockerContainerInfo(
                            DockerContainerState(),
                            DockerContainerConfiguration(
                                healthCheck = DockerContainerHealthCheckConfig()
                            )
                        )
                    )
                }

                on("waiting for that container to become healthy") {
                    val result by runForEachTest { client.waitForHealthStatus(container, cancellationContext) }

                    it("reports that the container does not have a health check") {
                        assertThat(result, equalTo(HealthStatus.NoHealthCheck))
                    }
                }
            }

            given("the Docker client returns an error when checking if the container has a health check") {
                val container = DockerContainer("the-container-id")

                beforeEachTest {
                    whenever(api.inspect(container)).thenThrow(ContainerInspectionFailedException("Something went wrong"))
                }

                on("waiting for that container to become healthy") {
                    it("throws an appropriate exception") {
                        assertThat({ client.waitForHealthStatus(container, cancellationContext) }, throws<ContainerHealthCheckException>(withMessage("Checking if container 'the-container-id' has a health check failed: Something went wrong")))
                    }
                }
            }

            given("a Docker container with a health check") {
                val container = DockerContainer("the-container-id")

                beforeEachTest {
                    whenever(api.inspect(container)).thenReturn(
                        DockerContainerInfo(
                            DockerContainerState(),
                            DockerContainerConfiguration(
                                healthCheck = DockerContainerHealthCheckConfig(
                                    test = listOf("some-command"),
                                    interval = Duration.ofSeconds(2),
                                    timeout = Duration.ofSeconds(1),
                                    startPeriod = Duration.ofSeconds(10),
                                    retries = 4
                                )
                            )
                        )
                    )
                }

                given("the health check passes") {
                    beforeEachTest {
                        whenever(api.waitForNextEvent(eq(container), eq(setOf("die", "health_status")), any(), eq(cancellationContext)))
                            .thenReturn(DockerEvent("health_status: healthy"))
                    }

                    on("waiting for that container to become healthy") {
                        val result by runForEachTest { client.waitForHealthStatus(container, cancellationContext) }

                        it("waits with a timeout that allows the container time to start and become healthy") {
                            verify(api).waitForNextEvent(any(), any(), eq(Duration.ofSeconds(10L + (3 * 4) + 1)), any())
                        }

                        it("reports that the container became healthy") {
                            assertThat(result, equalTo(HealthStatus.BecameHealthy))
                        }
                    }
                }

                given("the health check fails") {
                    beforeEachTest {
                        whenever(api.waitForNextEvent(eq(container), eq(setOf("die", "health_status")), any(), eq(cancellationContext)))
                            .thenReturn(DockerEvent("health_status: unhealthy"))
                    }

                    on("waiting for that container to become healthy") {
                        val result by runForEachTest { client.waitForHealthStatus(container, cancellationContext) }

                        it("reports that the container became unhealthy") {
                            assertThat(result, equalTo(HealthStatus.BecameUnhealthy))
                        }
                    }
                }

                given("the container exits before the health check reports") {
                    beforeEachTest {
                        whenever(api.waitForNextEvent(eq(container), eq(setOf("die", "health_status")), any(), eq(cancellationContext)))
                            .thenReturn(DockerEvent("die"))
                    }

                    on("waiting for that container to become healthy") {
                        val result by runForEachTest { client.waitForHealthStatus(container, cancellationContext) }

                        it("reports that the container exited") {
                            assertThat(result, equalTo(HealthStatus.Exited))
                        }
                    }
                }

                given("getting the next event for the container fails") {
                    beforeEachTest {
                        whenever(api.waitForNextEvent(eq(container), eq(setOf("die", "health_status")), any(), eq(cancellationContext)))
                            .thenThrow(DockerException("Something went wrong."))
                    }

                    on("waiting for that container to become healthy") {
                        it("throws an appropriate exception") {
                            assertThat({ client.waitForHealthStatus(container, cancellationContext) }, throws<ContainerHealthCheckException>(withMessage("Waiting for health status of container 'the-container-id' failed: Something went wrong.")))
                        }
                    }
                }
            }
        }

        describe("getting the last health check result for a container") {
            val container = DockerContainer("some-container")

            on("the container only having one last health check result") {
                val info = DockerContainerInfo(
                    DockerContainerState(
                        DockerContainerHealthCheckState(
                            listOf(
                                DockerHealthCheckResult(1, "something went wrong")
                            )
                        )
                    ),
                    DockerContainerConfiguration(DockerContainerHealthCheckConfig())
                )

                beforeEachTest { whenever(api.inspect(container)).doReturn(info) }

                val details by runForEachTest { client.getLastHealthCheckResult(container) }

                it("returns the details of the last health check result") {
                    assertThat(details, equalTo(DockerHealthCheckResult(1, "something went wrong")))
                }
            }

            on("the container having a full set of previous health check results") {
                val info = DockerContainerInfo(
                    DockerContainerState(
                        DockerContainerHealthCheckState(
                            listOf(
                                DockerHealthCheckResult(1, ""),
                                DockerHealthCheckResult(1, ""),
                                DockerHealthCheckResult(1, ""),
                                DockerHealthCheckResult(1, ""),
                                DockerHealthCheckResult(1, "this is the most recent health check")
                            )
                        )
                    ),
                    DockerContainerConfiguration(DockerContainerHealthCheckConfig())
                )

                beforeEachTest { whenever(api.inspect(container)).doReturn(info) }

                val details by runForEachTest { client.getLastHealthCheckResult(container) }

                it("returns the details of the last health check result") {
                    assertThat(details, equalTo(DockerHealthCheckResult(1, "this is the most recent health check")))
                }
            }

            on("the container not having a health check") {
                val info = DockerContainerInfo(
                    DockerContainerState(health = null),
                    DockerContainerConfiguration(DockerContainerHealthCheckConfig())
                )

                beforeEachTest { whenever(api.inspect(container)).doReturn(info) }

                it("throws an appropriate exception") {
                    assertThat(
                        { client.getLastHealthCheckResult(container) },
                        throws<ContainerHealthCheckException>(withMessage("Could not get the last health check result for container 'some-container'. The container does not have a health check."))
                    )
                }
            }

            on("getting the container's details failing") {
                beforeEachTest { whenever(api.inspect(container)).doThrow(ContainerInspectionFailedException("Something went wrong.")) }

                it("throws an appropriate exception") {
                    assertThat(
                        { client.getLastHealthCheckResult(container) },
                        throws<ContainerHealthCheckException>(withMessage("Could not get the last health check result for container 'some-container': Something went wrong."))
                    )
                }
            }
        }
    }
})
