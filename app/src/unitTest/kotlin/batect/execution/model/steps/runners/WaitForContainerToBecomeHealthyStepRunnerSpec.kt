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
import batect.docker.DockerContainer
import batect.dockerclient.Actor
import batect.dockerclient.ContainerConfig
import batect.dockerclient.ContainerHealthLogEntry
import batect.dockerclient.ContainerHealthState
import batect.dockerclient.ContainerHealthcheckConfig
import batect.dockerclient.ContainerHostConfig
import batect.dockerclient.ContainerInspectionResult
import batect.dockerclient.ContainerLogConfig
import batect.dockerclient.ContainerReference
import batect.dockerclient.ContainerState
import batect.dockerclient.DockerClient
import batect.dockerclient.Event
import batect.dockerclient.EventHandler
import batect.dockerclient.EventHandlerAction
import batect.execution.model.events.ContainerBecameHealthyEvent
import batect.execution.model.events.ContainerDidNotBecomeHealthyEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.WaitForContainerToBecomeHealthyStep
import batect.os.SystemInfo
import batect.primitives.CancellationContext
import batect.testutils.beforeEachTestSuspend
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.itSuspend
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.time.Duration.Companion.seconds

object WaitForContainerToBecomeHealthyStepRunnerSpec : Spek({
    describe("running a 'wait for container to become healthy' step") {
        val container = Container("some-container", imageSourceDoesNotMatter())
        val dockerContainer = DockerContainer(ContainerReference("some-id"), "some-name")
        val step = WaitForContainerToBecomeHealthyStep(container, dockerContainer)

        val dockerClient by createForEachTest { mock<DockerClient>() }
        val cancellationContext by createForEachTest { mock<CancellationContext>() }
        val systemInfo = mock<SystemInfo> {
            on { lineSeparator } doReturn "SYSTEM_LINE_SEPARATOR"
        }

        val eventSink by createForEachTest { mock<TaskEventSink>() }
        val logger by createLoggerForEachTest()
        val runner by createForEachTest { WaitForContainerToBecomeHealthyStepRunner(dockerClient, cancellationContext, systemInfo, logger) }

        fun createDummyInspectionResult(config: ContainerHealthcheckConfig?, state: ContainerHealthState?): ContainerInspectionResult =
            ContainerInspectionResult(
                ContainerReference("some-id"),
                "some-name",
                ContainerHostConfig(ContainerLogConfig("some-logger", emptyMap())),
                ContainerState(state),
                ContainerConfig(emptyMap(), config),
            )

        given("the container has no health check") {
            beforeEachTestSuspend {
                whenever(dockerClient.inspectContainer(ContainerReference("some-id")))
                    .doReturn(createDummyInspectionResult(ContainerHealthcheckConfig(emptyList(), 0.seconds, 0.seconds, 0.seconds, 0), null))
            }

            on("running the step") {
                beforeEachTest {
                    runner.run(step, eventSink)
                }

                it("emits a 'container became healthy' event") {
                    verify(eventSink).postEvent(ContainerBecameHealthyEvent(container))
                }

                itSuspend("does not wait for any events") {
                    verify(dockerClient, never()).streamEvents(anyOrNull(), anyOrNull(), any(), any())
                }
            }
        }

        given("the container has a health check") {
            fun setUpHealthCheckResult(exitCode: Long, output: String) {
                runBlocking {
                    whenever(dockerClient.inspectContainer(ContainerReference("some-id"))).doReturn(
                        createDummyInspectionResult(
                            ContainerHealthcheckConfig(listOf("healthcheck.sh"), null, null, null, null),
                            ContainerHealthState(
                                "this should not be used",
                                listOf(
                                    ContainerHealthLogEntry(Instant.DISTANT_PAST, Instant.DISTANT_PAST, 456, "This is a previous health check"),
                                    ContainerHealthLogEntry(Clock.System.now(), Clock.System.now(), exitCode, output),
                                ),
                            ),
                        ),
                    )
                }
            }

            fun setUpFirstEventForContainer(status: String) {
                runBlocking {
                    whenever(dockerClient.streamEvents(anyOrNull(), anyOrNull(), any(), any())).doAnswer { invocation ->
                        val handler = invocation.getArgument<EventHandler>(3)
                        val action = handler(Event("container", status, Actor("some-id", emptyMap()), "local", Clock.System.now()))

                        assertThat(action, equalTo(EventHandlerAction.Stop))
                    }
                }
            }

            given("the container becomes healthy") {
                beforeEachTest {
                    setUpHealthCheckResult(0, "")
                    setUpFirstEventForContainer("health_status: healthy")
                }

                on("running the step") {
                    beforeEachTest {
                        runner.run(step, eventSink)
                    }

                    it("emits a 'container became healthy' event") {
                        verify(eventSink).postEvent(ContainerBecameHealthyEvent(container))
                    }

                    itSuspend("waits for events with the correct set of filters") {
                        val expectedFilters = mapOf(
                            "container" to setOf("some-id"),
                            "event" to setOf("health_status", "die"),
                        )

                        verify(dockerClient).streamEvents(eq(Instant.fromEpochMilliseconds(0)), eq(null), eq(expectedFilters), any())
                    }
                }
            }

            given("the container becomes unhealthy") {
                beforeEachTest {
                    setUpFirstEventForContainer("health_status: unhealthy")
                }

                given("the last health check returned a non-zero exit code") {
                    given("and it produced no output") {
                        beforeEachTest {
                            setUpHealthCheckResult(123, "")
                        }

                        on("running the step") {
                            beforeEachTest {
                                runner.run(step, eventSink)
                            }

                            it("emits a 'container did not become healthy' event with details of the last health check") {
                                verify(eventSink).postEvent(
                                    ContainerDidNotBecomeHealthyEvent(
                                        container,
                                        "The configured health check did not indicate that the container was healthy within the timeout period. The last health check exited with code 123 but did not produce any output.",
                                    ),
                                )
                            }
                        }
                    }

                    given("and it produced some output") {
                        beforeEachTest {
                            setUpHealthCheckResult(123, "Something's not ready yet.\nMore output on next line.")
                        }

                        on("running the step") {
                            beforeEachTest {
                                runner.run(step, eventSink)
                            }

                            it("emits a 'container did not become healthy' event with details of the last health check with all line breaks replaced with the system line separator") {
                                verify(eventSink).postEvent(
                                    ContainerDidNotBecomeHealthyEvent(
                                        container,
                                        "The configured health check did not indicate that the container was healthy within the timeout period. The last health check exited with code 123 and output:SYSTEM_LINE_SEPARATORSomething's not ready yet.SYSTEM_LINE_SEPARATORMore output on next line.",
                                    ),
                                )
                            }
                        }
                    }
                }

                given("the last health check returned a zero exit code") {
                    beforeEachTest {
                        setUpHealthCheckResult(0, "This is some output from the health check.\nThis is some more output.")
                    }

                    on("running the step") {
                        beforeEachTest {
                            runner.run(step, eventSink)
                        }

                        it("emits a 'container did not become healthy' event with an explanation of the race condition") {
                            verify(eventSink).postEvent(
                                ContainerDidNotBecomeHealthyEvent(
                                    container,
                                    "The configured health check did not indicate that the container was healthy within the timeout period. The most recent health check exited with code 0, which usually indicates that the container became healthy just after the timeout period expired.",
                                ),
                            )
                        }
                    }
                }
            }

            given("the container exits without reporting any health information") {
                beforeEachTest {
                    setUpHealthCheckResult(0, "")
                    setUpFirstEventForContainer("die")
                }

                on("running the step") {
                    beforeEachTest {
                        runner.run(step, eventSink)
                    }

                    it("emits a 'container did not become healthy' event") {
                        verify(eventSink).postEvent(ContainerDidNotBecomeHealthyEvent(container, "The container exited before becoming healthy."))
                    }
                }
            }
        }
    }
})
