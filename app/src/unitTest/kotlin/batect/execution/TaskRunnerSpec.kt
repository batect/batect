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

package batect.execution

import batect.config.Container
import batect.config.Task
import batect.config.TaskRunConfiguration
import batect.ioc.TaskKodein
import batect.ioc.TaskKodeinFactory
import batect.telemetry.TelemetrySessionBuilder
import batect.telemetry.TelemetrySpanBuilder
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.ui.Console
import batect.ui.EventLogger
import batect.ui.text.Text
import batect.ui.text.TextRun
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import java.time.Duration
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TaskRunnerSpec : Spek({
    describe("a task runner") {
        val runOptions = RunOptions("some-task", emptyList(), CleanupOption.Cleanup, CleanupOption.Cleanup)

        val taskKodeinFactory by createForEachTest { mock<TaskKodeinFactory>() }
        val interruptionTrapCleanup by createForEachTest { mock<AutoCloseable>() }
        val interruptionTrap by createForEachTest {
            mock<InterruptionTrap> {
                on { trapInterruptions(any()) } doReturn interruptionTrapCleanup
            }
        }

        val console by createForEachTest { mock<Console>() }
        val telemetrySpanBuilder by createForEachTest { mock<TelemetrySpanBuilder>() }

        @Suppress("UNCHECKED_CAST")
        val telemetrySessionBuilder by createForEachTest {
            mock<TelemetrySessionBuilder> {
                onGeneric { addSpan<Int>(any(), any()) } doAnswer { invocation ->
                    val process = invocation.arguments[1] as ((TelemetrySpanBuilder) -> Int)
                    process(telemetrySpanBuilder)
                }
            }
        }

        val logger by createLoggerForEachTest()
        val taskRunner by createForEachTest { TaskRunner(taskKodeinFactory, interruptionTrap, console, telemetrySessionBuilder, logger) }

        describe("running a task") {
            given("the task has a container to run") {
                val container = Container("some-container", imageSourceDoesNotMatter())
                val runConfiguration = TaskRunConfiguration(container.name)
                val task = Task("some-task", runConfiguration)

                val eventLogger by createForEachTest { mock<EventLogger>() }
                val stateMachine by createForEachTest { mock<TaskStateMachine>() }
                val executionManager by createForEachTest { mock<ParallelExecutionManager>() }
                val dependencyGraph by createForEachTest {
                    mock<ContainerDependencyGraph> {
                        on { allContainers } doReturn setOf(container, Container("some-other-container", imageSourceDoesNotMatter()), Container("some-third-container", imageSourceDoesNotMatter()))
                    }
                }

                beforeEachTest {
                    whenever(taskKodeinFactory.create(any(), any())).thenReturn(TaskKodein(task, DI.direct {
                        bind<EventLogger>() with instance(eventLogger)
                        bind<TaskStateMachine>() with instance(stateMachine)
                        bind<ParallelExecutionManager>() with instance(executionManager)
                        bind<ContainerDependencyGraph>() with instance(dependencyGraph)
                    }))
                }

                given("the task succeeds") {
                    beforeEachTest {
                        whenever(stateMachine.taskHasFailed).thenReturn(false)
                        whenever(stateMachine.taskExitCode).thenReturn(100)
                        whenever(executionManager.run()).then { Thread.sleep(50) }
                    }

                    given("cleanup after success is enabled") {
                        on("running the task") {
                            val exitCode by runForEachTest { taskRunner.run(task, runOptions) }

                            it("logs that the task is starting") {
                                verify(eventLogger).onTaskStarting("some-task")
                            }

                            it("runs the task") {
                                verify(executionManager).run()
                            }

                            it("logs that the task is starting before running the task") {
                                inOrder(eventLogger, executionManager) {
                                    verify(eventLogger).onTaskStarting("some-task")
                                    verify(executionManager).run()
                                }
                            }

                            it("starts listening for interrupts before running the task, and stops listening after the task has finished") {
                                inOrder(interruptionTrap, executionManager, interruptionTrapCleanup) {
                                    verify(interruptionTrap).trapInterruptions(executionManager)
                                    verify(executionManager).run()
                                    verify(interruptionTrapCleanup).close()
                                }
                            }

                            it("logs that the task finished after running the task") {
                                inOrder(eventLogger, executionManager) {
                                    verify(executionManager).run()
                                    verify(eventLogger).onTaskFinished(eq("some-task"), eq(100), argThat { this >= Duration.ofMillis(50) })
                                }
                            }

                            it("returns the exit code from the task") {
                                assertThat(exitCode, equalTo(100))
                            }

                            it("does not write anything directly to the console") {
                                verifyZeroInteractions(console)
                            }

                            it("starts a telemetry span for the task") {
                                verify(telemetrySessionBuilder).addSpan(eq("RunTask"), any())
                            }

                            it("marks the span as not being for a task that only has prerequisites") {
                                verify(telemetrySpanBuilder).addAttribute("taskOnlyHasPrerequisites", false)
                            }

                            it("reports the number of containers in the task in telemetry") {
                                verify(telemetrySpanBuilder).addAttribute("containersInTask", 3)
                            }
                        }
                    }

                    given("cleanup after success is disabled") {
                        val runOptionsWithCleanupDisabled = runOptions.copy(behaviourAfterSuccess = CleanupOption.DontCleanup)

                        beforeEachTest {
                            whenever(stateMachine.manualCleanupInstructions).doReturn(TextRun("Do this to clean up"))
                        }

                        on("running the task") {
                            val exitCode by runForEachTest { taskRunner.run(task, runOptionsWithCleanupDisabled) }

                            it("logs that the task finished after running the task, then logs the manual cleanup instructions") {
                                inOrder(eventLogger, executionManager) {
                                    verify(executionManager).run()
                                    verify(eventLogger).onTaskFinished(eq("some-task"), eq(100), argThat { this >= Duration.ofMillis(50) })
                                    verify(eventLogger).onTaskFinishedWithCleanupDisabled(TextRun("Do this to clean up"))
                                }
                            }

                            it("returns a non-zero exit code") {
                                assertThat(exitCode, equalTo(-1))
                            }
                        }
                    }
                }

                given("the task fails") {
                    beforeEachTest {
                        whenever(stateMachine.taskHasFailed).thenReturn(true)
                        whenever(stateMachine.manualCleanupInstructions).thenReturn(TextRun("Do this to clean up"))
                    }

                    on("running the task") {
                        val exitCode by runForEachTest { taskRunner.run(task, runOptions) }

                        it("logs that the task is starting") {
                            verify(eventLogger).onTaskStarting("some-task")
                        }

                        it("runs the task") {
                            verify(executionManager).run()
                        }

                        it("logs that the task failed") {
                            verify(eventLogger).onTaskFailed("some-task", TextRun("Do this to clean up"))
                        }

                        it("logs that the task is starting before running the task and then logs that the task failed") {
                            inOrder(eventLogger, executionManager) {
                                verify(eventLogger).onTaskStarting("some-task")
                                verify(executionManager).run()
                                verify(eventLogger).onTaskFailed("some-task", TextRun("Do this to clean up"))
                            }
                        }

                        it("returns a non-zero exit code") {
                            assertThat(exitCode, !equalTo(0))
                        }

                        it("does not write anything directly to the console") {
                            verifyZeroInteractions(console)
                        }
                    }
                }
            }

            given("the task does not have a container to run") {
                val task = Task("some-task", runConfiguration = null)

                on("running the task") {
                    val exitCode by runForEachTest { taskRunner.run(task, runOptions) }

                    it("returns a zero exit code") {
                        assertThat(exitCode, equalTo(0))
                    }

                    it("does not create a task Kodein instance") {
                        verifyZeroInteractions(taskKodeinFactory)
                    }

                    it("writes a message to the console indicating that the task only has prerequisite tasks") {
                        verify(console).println(Text.white(Text("The task ") + Text.bold("some-task") + Text(" only defines prerequisite tasks, nothing more to do.")))
                    }

                    it("starts a telemetry span for the task") {
                        verify(telemetrySessionBuilder).addSpan(eq("RunTask"), any())
                    }

                    it("marks the span as being for a task that only has prerequisites") {
                        verify(telemetrySpanBuilder).addAttribute("taskOnlyHasPrerequisites", true)
                    }
                }
            }
        }
    }
})
