/*
   Copyright 2017 Charles Korn.

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

package batect

import batect.model.TaskStateMachine
import batect.model.events.TaskEvent
import batect.model.events.TaskEventSink
import batect.model.steps.FinishTaskStep
import batect.model.steps.TaskStep
import batect.model.steps.TaskStepRunner
import batect.testutils.CreateForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

object ParallelExecutionManagerSpec : Spek({
    describe("a parallel execution manager") {
        val eventLogger = mock<EventLogger>()
        val taskStepRunner = mock<TaskStepRunner>()
        val stateMachine = mock<TaskStateMachine>()
        val executionManager by CreateForEachTest(this) {
            ParallelExecutionManager(eventLogger, taskStepRunner, stateMachine, "some-task", 2)
        }

        beforeEachTest {
            reset(eventLogger)
            reset(taskStepRunner)
            reset(stateMachine)
        }

        on("no steps being provided by the state machine") {
            val exitCode = executionManager.run()

            it("logs that the task failed") {
                verify(eventLogger).logTaskFailed("some-task")
            }

            it("returns a non-zero exit code") {
                assertThat(exitCode, !equalTo(0))
            }
        }

        on("a single step being provided by the state machine") {
            val step = mock<TaskStep>()
            val eventToPost = mock<TaskEvent>()

            whenever(stateMachine.popNextStep()).doReturn(step, null)
            whenever(taskStepRunner.run(eq(step), any())).then { invocation ->
                val eventSink = invocation.arguments[1] as TaskEventSink
                eventSink.postEvent(eventToPost)

                null
            }

            executionManager.run()

            it("logs the step to the event logger") {
                verify(eventLogger).logBeforeStartingStep(step)
            }

            it("runs the step") {
                verify(taskStepRunner).run(eq(step), any())
            }

            it("logs the step to the event logger and then runs it") {
                inOrder(eventLogger, taskStepRunner) {
                    verify(eventLogger).logBeforeStartingStep(step)
                    verify(taskStepRunner).run(eq(step), any())
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

        on("a single 'finish task' step being provided by the state machine") {
            val step = FinishTaskStep(123)
            whenever(stateMachine.popNextStep()).doReturn(step, null)

            val exitCode = executionManager.run()

            it("logs the step to the event logger") {
                verify(eventLogger).logBeforeStartingStep(step)
            }

            it("runs the step") {
                verify(taskStepRunner).run(eq(step), any())
            }

            it("logs the step to the event logger and then runs it") {
                inOrder(eventLogger, taskStepRunner) {
                    verify(eventLogger).logBeforeStartingStep(step)
                    verify(taskStepRunner).run(eq(step), any())
                }
            }

            it("returns the exit code from the step") {
                assertThat(exitCode, equalTo(123))
            }
        }

        on("two steps being provided by the state machine initially") {
            val step1 = mock<TaskStep>()
            val step2 = mock<TaskStep>()
            whenever(stateMachine.popNextStep()).doReturn(step1, step2, null)

            var step1SawStep2 = false
            var step2SawStep1 = false
            val waitForStep1 = Semaphore(1)
            val waitForStep2 = Semaphore(1)
            waitForStep1.acquire()
            waitForStep2.acquire()

            whenever(taskStepRunner.run(eq(step1), any())).doAnswer {
                waitForStep1.release()
                step1SawStep2 = waitForStep2.tryAcquire(100, TimeUnit.MILLISECONDS)
                null
            }

            whenever(taskStepRunner.run(eq(step2), any())).doAnswer {
                waitForStep2.release()
                step2SawStep1 = waitForStep1.tryAcquire(100, TimeUnit.MILLISECONDS)
                null
            }

            executionManager.run()

            it("logs the first step to the event logger") {
                verify(eventLogger).logBeforeStartingStep(step1)
            }

            it("runs the first step") {
                verify(taskStepRunner).run(eq(step1), any())
            }

            it("logs the second step to the event logger") {
                verify(eventLogger).logBeforeStartingStep(step2)
            }

            it("runs the second step") {
                verify(taskStepRunner).run(eq(step2), any())
            }

            it("runs step 1 in parallel with step 2") {
                assertThat(step1SawStep2, equalTo(true))
                assertThat(step2SawStep1, equalTo(true))
            }
        }

        on("one step being provided by the state machine initially and then another added as a result of the first step") {
            val step1 = mock<TaskStep>()
            val step2 = mock<TaskStep>()
            val step2TriggerEvent = mock<TaskEvent>()
            whenever(stateMachine.popNextStep()).doReturn(step1, null)

            var step2StartedBeforeStep1Ended = false
            val waitForStep2 = Semaphore(1)
            waitForStep2.acquire()

            whenever(taskStepRunner.run(eq(step1), any())).doAnswer { invocation ->
                val eventSink = invocation.arguments[1] as TaskEventSink
                eventSink.postEvent(step2TriggerEvent)

                step2StartedBeforeStep1Ended = waitForStep2.tryAcquire(100, TimeUnit.MILLISECONDS)
                null
            }

            whenever(stateMachine.postEvent(step2TriggerEvent)).doAnswer {
                whenever(stateMachine.popNextStep()).doReturn(step2, null)
                null
            }

            whenever(taskStepRunner.run(eq(step2), any())).doAnswer {
                waitForStep2.release()
                null
            }

            executionManager.run()

            it("logs the first step to the event logger") {
                verify(eventLogger).logBeforeStartingStep(step1)
            }

            it("runs the first step") {
                verify(taskStepRunner).run(eq(step1), any())
            }

            it("logs the second step to the event logger") {
                verify(eventLogger).logBeforeStartingStep(step2)
            }

            it("runs the second step") {
                verify(taskStepRunner).run(eq(step2), any())
            }

            it("runs the first step in parallel with the second step") {
                assertThat(step2StartedBeforeStep1Ended, equalTo(true))
            }
        }

        on("three steps being provided by the state machine initially (one more than the maximum number of concurrent steps)") {
            val step1 = mock<TaskStep>()
            val step2 = mock<TaskStep>()
            val step3 = mock<TaskStep>()
            whenever(stateMachine.popNextStep()).doReturn(step1, step2, step3, null)

            val counter = Semaphore(2)
            val noMoreThanTwoTasksRunningAtTimeOfInvocation = ConcurrentHashMap<TaskStep, Boolean>()

            whenever(taskStepRunner.run(any(), any())).doAnswer { invocation ->
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

            it("logs the first step to the event logger") {
                verify(eventLogger).logBeforeStartingStep(step1)
            }

            it("runs the first step") {
                verify(taskStepRunner).run(eq(step1), any())
            }

            it("logs the second step to the event logger") {
                verify(eventLogger).logBeforeStartingStep(step2)
            }

            it("runs the second step") {
                verify(taskStepRunner).run(eq(step2), any())
            }

            it("logs the third step to the event logger") {
                verify(eventLogger).logBeforeStartingStep(step3)
            }

            it("runs the third step") {
                verify(taskStepRunner).run(eq(step3), any())
            }
        }
    }
})
