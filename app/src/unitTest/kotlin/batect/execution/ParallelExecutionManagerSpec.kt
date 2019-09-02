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

import batect.execution.model.events.ExecutionFailedEvent
import batect.execution.model.events.StepStartingEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskFailedEvent
import batect.execution.model.steps.CreateTaskNetworkStep
import batect.execution.model.steps.TaskStep
import batect.execution.model.steps.TaskStepRunner
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.given
import batect.testutils.on
import batect.ui.EventLogger
import batect.ui.containerio.ContainerIOStreamingOptions
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

object ParallelExecutionManagerSpec : Spek({
    describe("a parallel execution manager") {
        val ioStreamingOptions by createForEachTest { mock<ContainerIOStreamingOptions>() }
        val eventLogger by createForEachTest {
            mock<EventLogger> {
                on { it.ioStreamingOptions } doReturn ioStreamingOptions
            }
        }

        val taskStepRunner by createForEachTest { mock<TaskStepRunner>() }
        val cancellationContext by createForEachTest { mock<CancellationContext>() }
        val stateMachine by createForEachTest {
            mock<TaskStateMachine> {
                on { it.cancellationContext } doReturn cancellationContext
            }
        }

        val runOptions = RunOptions("some-task", emptyList(), CleanupOption.Cleanup, CleanupOption.Cleanup, true)
        val logger by createLoggerForEachTest()
        val executionManager by createForEachTest {
            ParallelExecutionManager(eventLogger, taskStepRunner, stateMachine, runOptions, logger)
        }

        fun eqExpectedRunContext() = argThat<TaskStepRunContext> { this.runOptions == runOptions && this.cancellationContext == cancellationContext && this.ioStreamingOptions == ioStreamingOptions }

        given("a single step is provided by the state machine") {
            val step by createForEachTest { mock<TaskStep>() }
            beforeEachTest { whenever(stateMachine.popNextStep(false)).doReturn(step, null) }

            given("that step runs successfully") {
                given("the step posts no events") {
                    on("running the task") {
                        beforeEachTest { executionManager.run() }

                        it("logs the step to the event logger") {
                            verify(eventLogger).postEvent(StepStartingEvent(step))
                        }

                        it("runs the step") {
                            verify(taskStepRunner).run(eq(step), eqExpectedRunContext())
                        }

                        it("logs the step to the event logger and then runs it") {
                            inOrder(eventLogger, taskStepRunner) {
                                verify(eventLogger).postEvent(StepStartingEvent(step))
                                verify(taskStepRunner).run(eq(step), eqExpectedRunContext())
                            }
                        }
                    }
                }

                given("the step posts an informational event") {
                    val eventToPost by createForEachTest {
                        mock<TaskEvent> {
                            on { isInformationalEvent } doReturn true
                        }
                    }

                    val stepThatShouldNotBeRun by createForEachTest { mock<TaskStep>() }

                    beforeEachTest {
                        whenever(taskStepRunner.run(eq(step), eqExpectedRunContext())).then { invocation ->
                            val context = invocation.arguments[1] as TaskStepRunContext

                            whenever(stateMachine.popNextStep(any())).doReturn(stepThatShouldNotBeRun, null)
                            context.eventSink.postEvent(eventToPost)
                            whenever(stateMachine.popNextStep(any())).doReturn(null)

                            null
                        }
                    }

                    on("running the task") {
                        beforeEachTest { executionManager.run() }

                        it("logs the posted event to the event logger") {
                            verify(eventLogger).postEvent(eventToPost)
                        }

                        it("does not forward the posted event to the state machine") {
                            verify(stateMachine, never()).postEvent(any())
                        }

                        it("does not queue any new work as a result of the event") {
                            verify(taskStepRunner, never()).run(eq(stepThatShouldNotBeRun), any())
                        }
                    }
                }

                given("the step posts a non-informational event") {
                    val eventToPost by createForEachTest {
                        mock<TaskEvent> {
                            on { isInformationalEvent } doReturn false
                        }
                    }

                    val stepTriggeredByEvent by createForEachTest { mock<TaskStep>() }

                    beforeEachTest {
                        whenever(taskStepRunner.run(eq(step), any())).then { invocation ->
                            val context = invocation.arguments[1] as TaskStepRunContext

                            whenever(stateMachine.popNextStep(any())).doReturn(stepTriggeredByEvent, null)
                            context.eventSink.postEvent(eventToPost)

                            null
                        }
                    }

                    on("running the task") {
                        beforeEachTest { executionManager.run() }

                        it("logs the posted event to the event logger") {
                            verify(eventLogger).postEvent(eventToPost)
                        }

                        it("forwards the posted event to the state machine") {
                            verify(stateMachine).postEvent(eventToPost)
                        }

                        it("logs the posted event to the event logger before forwarding it to the state machine") {
                            inOrder(eventLogger, stateMachine) {
                                verify(eventLogger).postEvent(eventToPost)
                                verify(stateMachine).postEvent(eventToPost)
                            }
                        }

                        it("queues any new work made available as a result of the event") {
                            verify(taskStepRunner).run(eq(stepTriggeredByEvent), any())
                        }
                    }
                }
            }
        }

        given("a step throws an exception during execution") {
            val step = CreateTaskNetworkStep

            beforeEachTest {
                whenever(stateMachine.popNextStep(false)).doReturn(step, null)
            }

            given("the exception is not because the step was cancelled") {
                beforeEachTest {
                    whenever(taskStepRunner.run(eq(step), eqExpectedRunContext())).thenThrow(RuntimeException("Something went wrong."))
                }

                on("running the task") {
                    beforeEachTest { executionManager.run() }

                    it("logs a task failure event to the event logger") {
                        verify(eventLogger).postEvent(ExecutionFailedEvent("During execution of step of kind 'CreateTaskNetworkStep': java.lang.RuntimeException: Something went wrong."))
                    }

                    it("logs a task failure event to the state machine") {
                        verify(stateMachine).postEvent(ExecutionFailedEvent("During execution of step of kind 'CreateTaskNetworkStep': java.lang.RuntimeException: Something went wrong."))
                    }
                }
            }

            given("the exception signals that the step was cancelled") {
                beforeEachTest {
                    whenever(taskStepRunner.run(eq(step), eqExpectedRunContext())).thenThrow(CancellationException("The step was cancelled"))
                }

                on("running the task") {
                    beforeEachTest { executionManager.run() }

                    it("does not log a task failure event to the event logger") {
                        verify(eventLogger, never()).postEvent(any<TaskFailedEvent>())
                    }

                    it("does not log a task failure event to the state machine") {
                        verify(stateMachine, never()).postEvent(any<TaskFailedEvent>())
                    }
                }
            }
        }

        on("two steps being provided by the state machine initially") {
            val step1 = mock<TaskStep>()
            val step2 = mock<TaskStep>()

            var step1SawStep2 = false
            var step2SawStep1 = false

            beforeEachTest {
                step1SawStep2 = false
                step2SawStep1 = false

                whenever(stateMachine.popNextStep(false)).doReturn(step1, null)
                whenever(stateMachine.popNextStep(true)).doReturn(step2, null)

                val waitForStep1 = Semaphore(1)
                val waitForStep2 = Semaphore(1)
                waitForStep1.acquire()
                waitForStep2.acquire()

                whenever(taskStepRunner.run(eq(step1), eqExpectedRunContext())).doAnswer {
                    waitForStep1.release()
                    step1SawStep2 = waitForStep2.tryAcquire(100, TimeUnit.MILLISECONDS)
                    null
                }

                whenever(taskStepRunner.run(eq(step2), eqExpectedRunContext())).doAnswer {
                    waitForStep2.release()
                    step2SawStep1 = waitForStep1.tryAcquire(100, TimeUnit.MILLISECONDS)
                    null
                }

                executionManager.run()
            }

            it("logs the first step to the event logger") {
                verify(eventLogger).postEvent(StepStartingEvent(step1))
            }

            it("runs the first step") {
                verify(taskStepRunner).run(eq(step1), eqExpectedRunContext())
            }

            it("logs the second step to the event logger") {
                verify(eventLogger).postEvent(StepStartingEvent(step2))
            }

            it("runs the second step") {
                verify(taskStepRunner).run(eq(step2), eqExpectedRunContext())
            }

            it("runs step 1 in parallel with step 2") {
                assertThat(step1SawStep2, equalTo(true))
                assertThat(step2SawStep1, equalTo(true))
            }
        }

        on("one step being provided by the state machine initially and then another added as a result of the first step") {
            val step1 = mock<TaskStep>()
            val step2 = mock<TaskStep>()

            var step2StartedBeforeStep1Ended = false

            beforeEachTest {
                step2StartedBeforeStep1Ended = false

                val step2TriggerEvent = mock<TaskEvent>()
                whenever(stateMachine.popNextStep(false)).doReturn(step1, null)

                val waitForStep2 = Semaphore(1)
                waitForStep2.acquire()

                whenever(taskStepRunner.run(eq(step1), eqExpectedRunContext())).doAnswer { invocation ->
                    val context = invocation.arguments[1] as TaskStepRunContext
                    context.eventSink.postEvent(step2TriggerEvent)

                    step2StartedBeforeStep1Ended = waitForStep2.tryAcquire(100, TimeUnit.MILLISECONDS)
                    null
                }

                whenever(stateMachine.postEvent(step2TriggerEvent)).doAnswer {
                    whenever(stateMachine.popNextStep(true)).doReturn(step2, null)
                    null
                }

                whenever(taskStepRunner.run(eq(step2), eqExpectedRunContext())).doAnswer {
                    waitForStep2.release()
                    null
                }

                executionManager.run()
            }

            it("logs the first step to the event logger") {
                verify(eventLogger).postEvent(StepStartingEvent(step1))
            }

            it("runs the first step") {
                verify(taskStepRunner).run(eq(step1), eqExpectedRunContext())
            }

            it("logs the second step to the event logger") {
                verify(eventLogger).postEvent(StepStartingEvent(step2))
            }

            it("runs the second step") {
                verify(taskStepRunner).run(eq(step2), eqExpectedRunContext())
            }

            it("runs the first step in parallel with the second step") {
                assertThat(step2StartedBeforeStep1Ended, equalTo(true))
            }
        }
    }
})
