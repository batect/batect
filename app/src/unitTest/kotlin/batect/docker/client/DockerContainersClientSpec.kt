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

package batect.docker.client

import batect.config.HealthCheckConfig
import batect.docker.ContainerHealthCheckException
import batect.docker.DockerContainer
import batect.docker.DockerContainerConfiguration
import batect.docker.DockerContainerCreationRequest
import batect.docker.DockerContainerHealthCheckConfig
import batect.docker.DockerContainerHealthCheckState
import batect.docker.DockerContainerInfo
import batect.docker.DockerContainerState
import batect.docker.DockerEvent
import batect.docker.DockerException
import batect.docker.DockerHealthCheckResult
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.docker.api.ContainerInspectionFailedException
import batect.docker.api.ContainersAPI
import batect.docker.run.ContainerIOStreamer
import batect.docker.run.ContainerInputStream
import batect.docker.run.ContainerOutputStream
import batect.docker.run.ContainerTTYManager
import batect.docker.run.ContainerWaiter
import batect.docker.run.InputConnection
import batect.docker.run.OutputConnection
import batect.execution.CancellationContext
import batect.os.ConsoleManager
import batect.os.Dimensions
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import okio.Sink
import okio.Source
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration
import java.util.concurrent.CompletableFuture

object DockerContainersClientSpec : Spek({
    describe("a Docker containers client") {
        val api by createForEachTest { mock<ContainersAPI>() }
        val consoleManager by createForEachTest { mock<ConsoleManager>() }
        val waiter by createForEachTest { mock<ContainerWaiter>() }
        val ioStreamer by createForEachTest { mock<ContainerIOStreamer>() }
        val ttyManager by createForEachTest { mock<ContainerTTYManager>() }
        val logger by createLoggerForEachTest()
        val client by createForEachTest { DockerContainersClient(api, consoleManager, waiter, ioStreamer, ttyManager, logger) }

        describe("creating a container") {
            given("a container configuration and a built image") {
                val image = DockerImage("the-image")
                val network = DockerNetwork("the-network")
                val command = listOf("doStuff")
                val entrypoint = listOf("sh")
                val request = DockerContainerCreationRequest("the-container-name", image, network, command, entrypoint, "some-host", setOf("some-host"), emptyMap(), "/some-dir", emptySet(), emptySet(), emptySet(), HealthCheckConfig(), null, false, false, emptySet(), emptySet())

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
                    whenever(waiter.startWaitingForContainerToExit(container, cancellationContext)).doReturn(CompletableFuture.completedFuture(123))
                    whenever(api.attachToOutput(container)).doReturn(outputStream)
                    whenever(api.attachToInput(container)).doReturn(inputStream)
                    whenever(consoleManager.enterRawMode()).doReturn(terminalRestorer)
                    whenever(ttyManager.monitorForSizeChanges(container, frameDimensions)).doReturn(resizingRestorer)
                }

                given("stdout is connected") {
                    val stdout by createForEachTest { mock<Sink>() }

                    given("stdin is connected") {
                        val stdin by createForEachTest { mock<Source>() }

                        on("running the container") {
                            val result by runForEachTest { client.run(container, stdout, stdin, cancellationContext, frameDimensions, onStartedHandler) }

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
                                    verify(api).attachToOutput(container)
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
                        }
                    }

                    given("stdin is not connected") {
                        val stdin: Source? = null

                        on("running the container") {
                            val result by runForEachTest { client.run(container, stdout, stdin, cancellationContext, frameDimensions, onStartedHandler) }

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
                                verify(api).attachToOutput(container)
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
                    val stdout: Sink? = null

                    given("stdin is connected") {
                        val stdin by createForEachTest { mock<Source>() }

                        it("throws an appropriate exception") {
                            assertThat({ client.run(container, stdout, stdin, cancellationContext, frameDimensions, onStartedHandler) }, throws<DockerException>(withMessage("Attempted to stream input to container without streaming container output.")))
                        }
                    }

                    given("stdin is not connected") {
                        val stdin: Source? = null

                        on("running the container") {
                            val result by runForEachTest { client.run(container, stdout, stdin, cancellationContext, frameDimensions, onStartedHandler) }

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
                                verify(api, never()).attachToOutput(container)
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
                            verify(api).waitForNextEvent(any(), any(), eq(Duration.ofSeconds(10 + (3 * 4) + 1)), any())
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
