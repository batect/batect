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

import batect.config.Container
import batect.docker.ContainerHealthCheckException
import batect.docker.DockerContainer
import batect.docker.DockerHealthCheckResult
import batect.docker.client.ContainersClient
import batect.docker.client.HealthStatus
import batect.execution.model.events.ContainerBecameHealthyEvent
import batect.execution.model.events.ContainerDidNotBecomeHealthyEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.WaitForContainerToBecomeHealthyStep
import batect.os.SystemInfo
import batect.primitives.CancellationContext
import batect.testutils.createForEachTest
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object WaitForContainerToBecomeHealthyStepRunnerSpec : Spek({
    describe("running a 'wait for container to become healthy' step") {
        val container = Container("some-container", imageSourceDoesNotMatter())
        val dockerContainer = DockerContainer("some-id")
        val step = WaitForContainerToBecomeHealthyStep(container, dockerContainer)

        val containersClient by createForEachTest { mock<ContainersClient>() }
        val cancellationContext by createForEachTest { mock<CancellationContext>() }
        val systemInfo = mock<SystemInfo> {
            on { lineSeparator } doReturn "SYSTEM_LINE_SEPARATOR"
        }

        val eventSink by createForEachTest { mock<TaskEventSink>() }
        val runner by createForEachTest { WaitForContainerToBecomeHealthyStepRunner(containersClient, cancellationContext, systemInfo) }

        on("when the container has no health check") {
            beforeEachTest {
                whenever(containersClient.waitForHealthStatus(dockerContainer, cancellationContext)).doReturn(HealthStatus.NoHealthCheck)

                runner.run(step, eventSink)
            }

            it("emits a 'container became healthy' event") {
                verify(eventSink).postEvent(ContainerBecameHealthyEvent(container))
            }
        }

        on("when the container becomes healthy") {
            beforeEachTest {
                whenever(containersClient.waitForHealthStatus(dockerContainer, cancellationContext)).doReturn(HealthStatus.BecameHealthy)

                runner.run(step, eventSink)
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

                        runner.run(step, eventSink)
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

                        runner.run(step, eventSink)
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

                    runner.run(step, eventSink)
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

                    runner.run(step, eventSink)
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

                runner.run(step, eventSink)
            }

            it("emits a 'container did not become healthy' event") {
                verify(eventSink).postEvent(ContainerDidNotBecomeHealthyEvent(container, "The container exited before becoming healthy."))
            }
        }

        on("when waiting for the container's health status fails") {
            beforeEachTest {
                whenever(containersClient.waitForHealthStatus(dockerContainer, cancellationContext)).doThrow(ContainerHealthCheckException("Something went wrong"))

                runner.run(step, eventSink)
            }

            it("emits a 'container did not become healthy' event") {
                verify(eventSink).postEvent(ContainerDidNotBecomeHealthyEvent(container, "Waiting for the container's health status failed: Something went wrong"))
            }
        }
    }
})
