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
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.steps.CreateTaskNetworkStep
import batect.execution.model.steps.TaskStep
import batect.execution.model.steps.TaskStepRunner
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.given
import batect.testutils.on
import batect.ui.EventLogger
import com.natpryce.hamkrest.allElements
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

object ParallelExecutionManagerSpec : Spek({
    describe("a parallel execution manager") {
        val eventLogger by createForEachTest { mock<EventLogger>() }
        val taskStepRunner by createForEachTest { mock<TaskStepRunner>() }
        val stateMachine by createForEachTest { mock<TaskStateMachine>() }
        val runOptions = RunOptions("some-task", emptyList(), 2, CleanupOption.Cleanup, true)
        val logger by createLoggerForEachTest()
        val executionManager by createForEachTest {
            ParallelExecutionManager(eventLogger, taskStepRunner, stateMachine, runOptions, logger)
        }

        given("a single step is provided by the state machine") {
            val step by createForEachTest { mock<TaskStep>() }
            beforeEachTest { whenever(stateMachine.popNextStep(false)).doReturn(step, null) }

            given("that step runs successfully") {
                val eventToPost by createForEachTest { mock<TaskEvent>() }

                beforeEachTest {
                    whenever(taskStepRunner.run(eq(step), any(), eq(runOptions))).then { invocation ->
                        val eventSink = invocation.arguments[1] as TaskEventSink
                        eventSink.postEvent(eventToPost)

                        null
                    }
                }

                on("running the task") {
                    beforeEachTest { executionManager.run() }

                    it("logs the step to the event logger") {
                        verify(eventLogger).onStartingTaskStep(step)
                    }

                    it("runs the step") {
                        verify(taskStepRunner).run(eq(step), any(), eq(runOptions))
                    }

                    it("logs the step to the event logger and then runs it") {
                        inOrder(eventLogger, taskStepRunner) {
                            verify(eventLogger).onStartingTaskStep(step)
                            verify(taskStepRunner).run(eq(step), any(), eq(runOptions))
                        }
                    }

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
                }
            }
        }

        given("a step throws an exception during execution") {
            val step = CreateTaskNetworkStep

            beforeEachTest {
                whenever(stateMachine.popNextStep(false)).doReturn(step, null)
                whenever(taskStepRunner.run(eq(step), any(), eq(runOptions))).thenThrow(RuntimeException("Something went wrong."))
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

                whenever(taskStepRunner.run(eq(step1), any(), eq(runOptions))).doAnswer {
                    waitForStep1.release()
                    step1SawStep2 = waitForStep2.tryAcquire(100, TimeUnit.MILLISECONDS)
                    null
                }

                whenever(taskStepRunner.run(eq(step2), any(), eq(runOptions))).doAnswer {
                    waitForStep2.release()
                    step2SawStep1 = waitForStep1.tryAcquire(100, TimeUnit.MILLISECONDS)
                    null
                }

                executionManager.run()
            }

            it("logs the first step to the event logger") {
                verify(eventLogger).onStartingTaskStep(step1)
            }

            it("runs the first step") {
                verify(taskStepRunner).run(eq(step1), any(), eq(runOptions))
            }

            it("logs the second step to the event logger") {
                verify(eventLogger).onStartingTaskStep(step2)
            }

            it("runs the second step") {
                verify(taskStepRunner).run(eq(step2), any(), eq(runOptions))
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

                whenever(taskStepRunner.run(eq(step1), any(), eq(runOptions))).doAnswer { invocation ->
                    val eventSink = invocation.arguments[1] as TaskEventSink
                    eventSink.postEvent(step2TriggerEvent)

                    step2StartedBeforeStep1Ended = waitForStep2.tryAcquire(100, TimeUnit.MILLISECONDS)
                    null
                }

                whenever(stateMachine.postEvent(step2TriggerEvent)).doAnswer {
                    whenever(stateMachine.popNextStep(true)).doReturn(step2, null)
                    null
                }

                whenever(taskStepRunner.run(eq(step2), any(), eq(runOptions))).doAnswer {
                    waitForStep2.release()
                    null
                }

                executionManager.run()
            }

            it("logs the first step to the event logger") {
                verify(eventLogger).onStartingTaskStep(step1)
            }

            it("runs the first step") {
                verify(taskStepRunner).run(eq(step1), any(), eq(runOptions))
            }

            it("logs the second step to the event logger") {
                verify(eventLogger).onStartingTaskStep(step2)
            }

            it("runs the second step") {
                verify(taskStepRunner).run(eq(step2), any(), eq(runOptions))
            }

            it("runs the first step in parallel with the second step") {
                assertThat(step2StartedBeforeStep1Ended, equalTo(true))
            }
        }

        on("three steps being provided by the state machine initially (one more than the maximum number of concurrent steps)") {
            val step1 = mock<TaskStep>()
            val step2 = mock<TaskStep>()
            val step3 = mock<TaskStep>()

            val noMoreThanTwoTasksRunningAtTimeOfInvocation by createForEachTest { ConcurrentHashMap<TaskStep, Boolean>() }

            beforeEachTest {
                whenever(stateMachine.popNextStep(any())).doReturn(step1, step2, step3, null)

                val counter = Semaphore(2)

                whenever(taskStepRunner.run(any(), any(), eq(runOptions))).doAnswer { invocation ->
                    val step = invocation.arguments[0] as TaskStep
                    val acquired = counter.tryAcquire()

                    noMoreThanTwoTasksRunningAtTimeOfInvocation[step] = acquired
                    Thread.sleep(50)

                    if (acquired) {
                        counter.release()
                    }

                    null
                }

                executionManager.run()
            }

            it("logs the first step to the event logger") {
                verify(eventLogger).onStartingTaskStep(step1)
            }

            it("runs the first step") {
                verify(taskStepRunner).run(eq(step1), any(), eq(runOptions))
            }

            it("logs the second step to the event logger") {
                verify(eventLogger).onStartingTaskStep(step2)
            }

            it("runs the second step") {
                verify(taskStepRunner).run(eq(step2), any(), eq(runOptions))
            }

            it("logs the third step to the event logger") {
                verify(eventLogger).onStartingTaskStep(step3)
            }

            it("runs the third step") {
                verify(taskStepRunner).run(eq(step3), any(), eq(runOptions))
            }

            it("does not run more than two steps at the same time") {
                assertThat(noMoreThanTwoTasksRunningAtTimeOfInvocation.values, allElements(equalTo(true)))
            }
        }

        on("being sent an event") {
            val eventToPost = mock<TaskEvent>()

            beforeEachTest { executionManager.postEvent(eventToPost) }

            it("logs the posted event to the event logger before forwarding it to the state machine") {
                inOrder(eventLogger, stateMachine) {
                    verify(eventLogger).postEvent(eventToPost)
                    verify(stateMachine).postEvent(eventToPost)
                }
            }
        }
    }
})
