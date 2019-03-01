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

package batect.execution

import batect.config.Configuration
import batect.config.Container
import batect.config.ContainerMap
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.execution.model.events.RunningContainerExitedEvent
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import batect.ui.EventLogger
import batect.ui.EventLoggerProvider
import batect.ui.text.TextRun
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration

object TaskRunnerSpec : Spek({
    describe("a task runner") {
        val container = Container("some-container", imageSourceDoesNotMatter())
        val runConfiguration = TaskRunConfiguration(container.name)
        val task = Task("some-task", runConfiguration)
        val config = Configuration("some-project", TaskMap(task), ContainerMap(container))
        val runOptions = RunOptions("some-task", emptyList(), 64, BehaviourAfterFailure.Cleanup, true)

        val graph = mock<ContainerDependencyGraph>()
        val graphProvider = mock<ContainerDependencyGraphProvider> {
            on { createGraph(config, task) } doReturn graph
        }

        val eventLogger by createForEachTest { mock<EventLogger>() }
        val eventLoggerProvider by createForEachTest {
            mock<EventLoggerProvider> {
                on { getEventLogger(graph, runOptions) } doReturn eventLogger
            }
        }

        val stateMachine by createForEachTest { mock<TaskStateMachine>() }
        val executionManager by createForEachTest { mock<ParallelExecutionManager>() }

        val stateMachineProvider by createForEachTest {
            mock<TaskStateMachineProvider> {
                on { createStateMachine(graph, runOptions) } doReturn stateMachine
            }
        }

        val executionManagerProvider by createForEachTest {
            mock<ParallelExecutionManagerProvider> {
                on { createParallelExecutionManager(eventLogger, stateMachine, runOptions) } doReturn executionManager
            }
        }

        val interruptionTrapCleanup by createForEachTest { mock<AutoCloseable>() }
        val interruptionTrap by createForEachTest {
            mock<InterruptionTrap> {
                on { trapInterruptions(any()) } doReturn interruptionTrapCleanup
            }
        }

        val logger by createLoggerForEachTest()
        val taskRunner by createForEachTest { TaskRunner(eventLoggerProvider, graphProvider, stateMachineProvider, executionManagerProvider, interruptionTrap, logger) }

        describe("running a task") {
            given("the task succeeds") {
                beforeEachTest {
                    whenever(stateMachine.taskHasFailed).thenReturn(false)
                    whenever(stateMachine.getAllEvents()).thenReturn(setOf(
                        RunningContainerExitedEvent(container, 100)
                    ))
                    whenever(executionManager.run()).then { Thread.sleep(50) }
                }

                on("running the task") {
                    val exitCode by runForEachTest { taskRunner.run(config, task, runOptions) }

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
                }
            }

            given("the task fails") {
                beforeEachTest {
                    whenever(stateMachine.taskHasFailed).thenReturn(true)
                    whenever(stateMachine.manualCleanupInstructions).thenReturn(TextRun("Do this to clean up"))
                }

                on("running the task") {
                    val exitCode by runForEachTest { taskRunner.run(config, task, runOptions) }

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
                }
            }

            given("the task neither succeeds or fails") {
                beforeEachTest {
                    whenever(stateMachine.taskHasFailed).thenReturn(false)
                    whenever(stateMachine.getAllEvents()).thenReturn(emptySet())
                }

                on("running the task") {
                    it("throws an appropriate exception") {
                        assertThat({ taskRunner.run(config, task, runOptions) }, throws<IllegalStateException>(withMessage("The task neither failed nor succeeded.")))
                    }
                }
            }
        }
    }
})
