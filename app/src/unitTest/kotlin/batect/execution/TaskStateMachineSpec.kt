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
import batect.docker.DockerContainer
import batect.docker.DockerNetwork
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.RunningContainerExitedEvent
import batect.execution.model.events.TaskFailedEvent
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.events.TaskNetworkDeletedEvent
import batect.execution.model.stages.CleanupStage
import batect.execution.model.stages.CleanupStagePlanner
import batect.execution.model.stages.NoStepsReady
import batect.execution.model.stages.RunStage
import batect.execution.model.stages.RunStagePlanner
import batect.execution.model.stages.StageComplete
import batect.execution.model.stages.StepReady
import batect.execution.model.steps.TaskStep
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.runNullableForEachTest
import batect.testutils.withMessage
import batect.ui.FailureErrorMessageFormatter
import batect.ui.text.TextRun
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe

object TaskStateMachineSpec : Spek({
    describe("a task state machine") {
        val graph by createForEachTest { mock<ContainerDependencyGraph>() }
        val runOptions by createForEachTest { mock<RunOptions>() }
        val logger by createLoggerForEachTest()
        val runStage by createForEachTest { mock<RunStage>() }
        val runStagePlanner by createForEachTest {
            mock<RunStagePlanner> {
                on { createStage() } doReturn runStage
            }
        }

        val cleanupCommands = listOf("rm /tmp/the-file", "rm /tmp/some-other-file")
        val cleanupStage by createForEachTest {
            mock<CleanupStage> {
                on { manualCleanupInstructions } doReturn cleanupCommands
            }
        }

        val cleanupStagePlanner by createForEachTest {
            mock<CleanupStagePlanner> {
                on { createStage(any()) } doReturn cleanupStage
            }
        }

        val failureErrorMessageFormatter by createForEachTest { mock<FailureErrorMessageFormatter>() }
        val cancellationContext by createForEachTest { mock<CancellationContext>() }

        val stateMachine by createForEachTest { TaskStateMachine(graph, runOptions, runStagePlanner, cleanupStagePlanner, failureErrorMessageFormatter, cancellationContext, logger) }

        describe("posting events") {
            given("the event is a failure event") {
                val event = mock<TaskFailedEvent>()

                given("the run stage is active") {
                    on("posting the event") {
                        beforeEachTest { stateMachine.postEvent(event) }

                        it("cancels all currently running operations") {
                            verify(cancellationContext).cancel()
                        }
                    }
                }

                given("the cleanup stage is active") {
                    beforeEachTest {
                        whenever(runStage.popNextStep(emptySet(), false)).doReturn(StageComplete)
                        whenever(cleanupStage.popNextStep(emptySet(), false)).doReturn(StepReady(mock()))
                        stateMachine.popNextStep(false)
                    }

                    on("posting the event") {
                        beforeEachTest { stateMachine.postEvent(event) }

                        it("does not cancel all currently running operations") {
                            verify(cancellationContext, never()).cancel()
                        }
                    }
                }
            }
        }

        describe("getting the next step to execute") {
            given("the run stage is active") {
                given("the task has not failed") {
                    given("there is a step ready") {
                        regardlessOfWhetherThereAreStepsRunning { stepsStillRunning ->
                            val step by createForEachTest { mock<TaskStep>() }
                            beforeEachTest { whenever(runStage.popNextStep(any(), eq(stepsStillRunning))).doReturn(StepReady(step)) }

                            on("getting the next step to execute") {
                                val events = setOf(TaskNetworkCreatedEvent(DockerNetwork("some-network")), TaskNetworkDeletedEvent)

                                beforeEachTest { events.forEach { stateMachine.postEvent(it) } }

                                val result by runNullableForEachTest { stateMachine.popNextStep(stepsStillRunning) }

                                it("returns that step") {
                                    assertThat(result, equalTo(step))
                                }

                                it("sends all previous events to the run stage during evaluation") {
                                    verify(runStage).popNextStep(events, stepsStillRunning)
                                }

                                it("does not create the cleanup stage") {
                                    verify(cleanupStagePlanner, never()).createStage(any())
                                }
                            }
                        }
                    }

                    given("there are no steps ready") {
                        beforeEachTest { whenever(runStage.popNextStep(any(), any())).doReturn(NoStepsReady) }

                        given("there are steps still running") {
                            val stepsStillRunning = true

                            on("getting the next step to execute") {
                                val result by runNullableForEachTest { stateMachine.popNextStep(stepsStillRunning) }

                                it("returns null") {
                                    assertThat(result, absent())
                                }

                                it("does not create the cleanup stage") {
                                    verify(cleanupStagePlanner, never()).createStage(any())
                                }
                            }
                        }

                        given("there are no steps still running") {
                            val stepsStillRunning = false

                            on("getting the next step to execute") {
                                it("throws an appropriate exception") {
                                    assertThat({ stateMachine.popNextStep(stepsStillRunning) }, throws<IllegalStateException>(withMessage("None of the remaining steps are ready to execute, but there are no steps currently running.")))
                                }
                            }
                        }
                    }

                    given("the run stage is complete") {
                        val event = TaskNetworkCreatedEvent(DockerNetwork("some-network"))

                        regardlessOfWhetherThereAreStepsRunning { stepsStillRunning ->
                            beforeEachTest {
                                val step = mock<TaskStep>()

                                whenever(runStage.popNextStep(emptySet(), stepsStillRunning)).doReturn(StepReady(step))
                                whenever(runStage.popNextStep(setOf(event), stepsStillRunning)).doReturn(StageComplete)

                                val firstStep = stateMachine.popNextStep(stepsStillRunning)
                                assertThat(firstStep, equalTo(step))
                                stateMachine.postEvent(event)
                            }

                            given("cleanup after success is enabled") {
                                beforeEachTest { whenever(runOptions.behaviourAfterSuccess) doReturn CleanupOption.Cleanup }

                                on("getting the next step to execute") {
                                    val cleanupStep = mock<TaskStep>()
                                    beforeEachTest { whenever(cleanupStage.popNextStep(setOf(event), stepsStillRunning)).doReturn(StepReady(cleanupStep)) }

                                    val result by runNullableForEachTest { stateMachine.popNextStep(stepsStillRunning) }

                                    it("returns the first step from the cleanup stage") {
                                        assertThat(result, equalTo(cleanupStep))
                                    }

                                    it("sends all previous events to the cleanup stage planner") {
                                        verify(cleanupStagePlanner).createStage(setOf(event))
                                    }
                                }
                            }

                            given("cleanup after success is disabled") {
                                beforeEachTest { whenever(runOptions.behaviourAfterSuccess) doReturn CleanupOption.DontCleanup }

                                on("getting the next steps to execute") {
                                    beforeEachTest {
                                        whenever(failureErrorMessageFormatter.formatManualCleanupMessageAfterTaskSuccessWithCleanupDisabled(setOf(event), cleanupCommands)).doReturn(TextRun("Do this to clean up"))
                                    }

                                    val cleanupStepThatShouldNeverBeRun = mock<TaskStep>()
                                    beforeEachTest { whenever(cleanupStage.popNextStep(setOf(event), stepsStillRunning)).doReturn(StepReady(cleanupStepThatShouldNeverBeRun)) }

                                    val result by runNullableForEachTest { stateMachine.popNextStep(stepsStillRunning) }

                                    it("returns null") {
                                        assertThat(result, absent())
                                    }

                                    it("sends all previous events to the cleanup stage planner") {
                                        verify(cleanupStagePlanner).createStage(setOf(event))
                                    }

                                    it("sets the cleanup instruction to that provided by the error message formatter") {
                                        assertThat(stateMachine.manualCleanupInstructions, equalTo(TextRun("Do this to clean up")))
                                    }

                                    it("does not indicate that the task has failed") {
                                        assertThat(stateMachine.taskHasFailed, equalTo(false))
                                    }
                                }
                            }
                        }
                    }
                }

                given("the task has failed") {
                    val failureEvent = mock<TaskFailedEvent>()
                    beforeEachTest { stateMachine.postEvent(failureEvent) }

                    given("there are steps still running") {
                        val stepsStillRunning = true

                        on("getting the next step to execute") {
                            val result by runNullableForEachTest { stateMachine.popNextStep(stepsStillRunning) }

                            it("returns null") {
                                assertThat(result, absent())
                            }

                            it("does not pop any steps from the run stage") {
                                verify(runStage, never()).popNextStep(any(), any())
                            }

                            it("does not create the cleanup stage") {
                                verify(cleanupStagePlanner, never()).createStage(any())
                            }
                        }
                    }

                    given("there are no steps still running") {
                        val stepsStillRunning = false

                        given("cleanup after failure is enabled") {
                            beforeEachTest { whenever(runOptions.behaviourAfterFailure) doReturn CleanupOption.Cleanup }

                            on("getting the next steps to execute") {
                                val firstCleanupStep = mock<TaskStep>()
                                val secondCleanupStep = mock<TaskStep>()

                                beforeEachTest { whenever(cleanupStage.popNextStep(setOf(failureEvent), stepsStillRunning)).doReturn(StepReady(firstCleanupStep), StepReady(secondCleanupStep)) }

                                val firstResult by runNullableForEachTest { stateMachine.popNextStep(stepsStillRunning) }
                                val secondResult by runNullableForEachTest { stateMachine.popNextStep(stepsStillRunning) }

                                it("returns the first step from the cleanup stage on the first call") {
                                    assertThat(firstResult, equalTo(firstCleanupStep))
                                }

                                it("returns the second step from the cleanup stage on the second call") {
                                    assertThat(secondResult, equalTo(secondCleanupStep))
                                }

                                it("does not pop any steps from the run stage") {
                                    verify(runStage, never()).popNextStep(any(), any())
                                }

                                it("sends all previous events to the cleanup stage planner, and only creates the cleanup stage once") {
                                    verify(cleanupStagePlanner, times(1)).createStage(setOf(failureEvent))
                                }
                            }
                        }

                        given("cleanup after failure is disabled") {
                            beforeEachTest { whenever(runOptions.behaviourAfterFailure) doReturn CleanupOption.DontCleanup }

                            given("no containers have been created") {
                                on("getting the next steps to execute") {
                                    val firstCleanupStep = mock<TaskStep>()
                                    val secondCleanupStep = mock<TaskStep>()

                                    beforeEachTest { whenever(cleanupStage.popNextStep(setOf(failureEvent), stepsStillRunning)).doReturn(StepReady(firstCleanupStep), StepReady(secondCleanupStep)) }

                                    val firstResult by runNullableForEachTest { stateMachine.popNextStep(stepsStillRunning) }
                                    val secondResult by runNullableForEachTest { stateMachine.popNextStep(stepsStillRunning) }

                                    it("returns the first step from the cleanup stage on the first call") {
                                        assertThat(firstResult, equalTo(firstCleanupStep))
                                    }

                                    it("returns the second step from the cleanup stage on the second call") {
                                        assertThat(secondResult, equalTo(secondCleanupStep))
                                    }

                                    it("does not pop any steps from the run stage") {
                                        verify(runStage, never()).popNextStep(any(), any())
                                    }

                                    it("sends all previous events to the cleanup stage planner, and only creates the cleanup stage once") {
                                        verify(cleanupStagePlanner, times(1)).createStage(setOf(failureEvent))
                                    }
                                }
                            }

                            given("at least one container has been created") {
                                val container1 = Container("container-1", imageSourceDoesNotMatter())
                                val container2 = Container("container-2", imageSourceDoesNotMatter())
                                val dockerContainer1 = DockerContainer("docker-container-1")
                                val dockerContainer2 = DockerContainer("docker-container-2")
                                val event1 = ContainerCreatedEvent(container1, dockerContainer1)
                                val event2 = ContainerCreatedEvent(container2, dockerContainer2)
                                val events = setOf(failureEvent, event1, event2)

                                beforeEachTest {
                                    stateMachine.postEvent(event1)
                                    stateMachine.postEvent(event2)

                                    whenever(failureErrorMessageFormatter.formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(events, cleanupCommands)).doReturn(TextRun("Do this to clean up"))
                                }

                                on("getting the next steps to execute") {
                                    val cleanupStepThatShouldNeverBeRun = mock<TaskStep>()
                                    beforeEachTest { whenever(cleanupStage.popNextStep(events, stepsStillRunning)).doReturn(StepReady(cleanupStepThatShouldNeverBeRun)) }

                                    val result by runNullableForEachTest { stateMachine.popNextStep(stepsStillRunning) }

                                    it("returns null") {
                                        assertThat(result, absent())
                                    }

                                    it("does not pop any steps from the run stage") {
                                        verify(runStage, never()).popNextStep(any(), any())
                                    }

                                    it("sends all previous events to the cleanup stage planner") {
                                        verify(cleanupStagePlanner).createStage(events)
                                    }

                                    it("sets the cleanup instruction to that provided by the error message formatter") {
                                        assertThat(stateMachine.manualCleanupInstructions, equalTo(TextRun("Do this to clean up")))
                                    }

                                    it("indicates that the task has failed") {
                                        assertThat(stateMachine.taskHasFailed, equalTo(true))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            given("the cleanup stage is active") {
                given("the task did not fail") {
                    val previousEvents = setOf(TaskNetworkCreatedEvent(DockerNetwork("some-network")), TaskNetworkDeletedEvent)

                    beforeEachTest {
                        whenever(runStage.popNextStep(emptySet(), false)).doReturn(StageComplete)
                        whenever(cleanupStage.popNextStep(emptySet(), false)).doReturn(StepReady(mock()))
                        stateMachine.popNextStep(false)

                        previousEvents.forEach { stateMachine.postEvent(it) }
                    }

                    given("cleanup has not failed") {
                        given("there is a step ready") {
                            regardlessOfWhetherThereAreStepsRunning { stepsStillRunning ->
                                val step by createForEachTest { mock<TaskStep>() }
                                beforeEachTest { whenever(cleanupStage.popNextStep(previousEvents, stepsStillRunning)).doReturn(StepReady(step)) }

                                on("getting the next step to execute") {
                                    val result by runNullableForEachTest { stateMachine.popNextStep(stepsStillRunning) }

                                    it("returns that step") {
                                        assertThat(result, equalTo(step))
                                    }
                                }
                            }
                        }

                        given("there are no steps ready") {
                            beforeEachTest { whenever(cleanupStage.popNextStep(eq(previousEvents), any())).doReturn(NoStepsReady) }

                            given("there are steps still running") {
                                val stepsStillRunning = true

                                on("getting the next step to execute") {
                                    val result by runNullableForEachTest { stateMachine.popNextStep(stepsStillRunning) }

                                    it("returns null") {
                                        assertThat(result, absent())
                                    }
                                }
                            }

                            given("there are no steps still running") {
                                val stepsStillRunning = false

                                on("getting the next step to execute") {
                                    it("throws an appropriate exception") {
                                        assertThat({ stateMachine.popNextStep(stepsStillRunning) }, throws<IllegalStateException>(withMessage("None of the remaining steps are ready to execute, but there are no steps currently running.")))
                                    }
                                }

                                on("attempting to get information about the state of the task after attempting to get the next step") {
                                    beforeEachTest {
                                        try {
                                            stateMachine.popNextStep(stepsStillRunning)
                                        } catch (_: Throwable) {
                                            // Ignore - we test this exception above.
                                        }
                                    }

                                    it("does not provide any cleanup instructions") {
                                        assertThat(stateMachine.manualCleanupInstructions, equalTo(TextRun()))
                                    }

                                    it("indicates that the task has failed") {
                                        assertThat(stateMachine.taskHasFailed, equalTo(true))
                                    }
                                }
                            }
                        }

                        given("the cleanup stage is complete") {
                            regardlessOfWhetherThereAreStepsRunning { stepsStillRunning ->
                                beforeEachTest { whenever(cleanupStage.popNextStep(previousEvents, stepsStillRunning)).doReturn(StageComplete) }

                                on("getting the next step to execute") {
                                    val result by runNullableForEachTest { stateMachine.popNextStep(stepsStillRunning) }

                                    it("returns null") {
                                        assertThat(result, absent())
                                    }

                                    it("does not provide any cleanup instructions") {
                                        assertThat(stateMachine.manualCleanupInstructions, equalTo(TextRun()))
                                    }

                                    it("indicates that the task has succeeded") {
                                        assertThat(stateMachine.taskHasFailed, equalTo(false))
                                    }
                                }
                            }
                        }
                    }

                    given("cleanup has failed") {
                        val event = mock<TaskFailedEvent>()
                        val previousEventsWithFailureEvent = previousEvents + event

                        beforeEachTest {
                            whenever(failureErrorMessageFormatter.formatManualCleanupMessageAfterCleanupFailure(cleanupCommands)).doReturn(TextRun("Do this to clean up"))

                            stateMachine.postEvent(event)
                        }

                        given("there is a step ready") {
                            regardlessOfWhetherThereAreStepsRunning { stepsStillRunning ->
                                val step by createForEachTest { mock<TaskStep>() }
                                beforeEachTest { whenever(cleanupStage.popNextStep(previousEventsWithFailureEvent, stepsStillRunning)).doReturn(StepReady(step)) }

                                on("getting the next step to execute") {
                                    val result by runNullableForEachTest { stateMachine.popNextStep(stepsStillRunning) }

                                    it("returns that step") {
                                        assertThat(result, equalTo(step))
                                    }
                                }
                            }
                        }

                        given("there are no steps ready") {
                            beforeEachTest { whenever(cleanupStage.popNextStep(eq(previousEventsWithFailureEvent), any())).doReturn(NoStepsReady) }

                            given("there are steps still running") {
                                val stepsStillRunning = true

                                on("getting the next step to execute") {
                                    val result by runNullableForEachTest { stateMachine.popNextStep(stepsStillRunning) }

                                    it("returns null") {
                                        assertThat(result, absent())
                                    }
                                }
                            }

                            given("there are no steps still running") {
                                val stepsStillRunning = false

                                on("getting the next step to execute") {
                                    val result by runNullableForEachTest { stateMachine.popNextStep(stepsStillRunning) }

                                    it("returns null") {
                                        assertThat(result, absent())
                                    }

                                    it("sets the cleanup instruction to that provided by the error message formatter") {
                                        assertThat(stateMachine.manualCleanupInstructions, equalTo(TextRun("Do this to clean up")))
                                    }
                                }
                            }
                        }

                        given("the cleanup stage has completed") {
                            regardlessOfWhetherThereAreStepsRunning { stepsStillRunning ->
                                beforeEachTest { whenever(cleanupStage.popNextStep(previousEventsWithFailureEvent, stepsStillRunning)).doReturn(StageComplete) }

                                on("getting the next step to execute") {
                                    val result by runNullableForEachTest { stateMachine.popNextStep(stepsStillRunning) }

                                    it("returns null") {
                                        assertThat(result, absent())
                                    }

                                    it("sets the cleanup instruction to that provided by the error message formatter") {
                                        assertThat(stateMachine.manualCleanupInstructions, equalTo(TextRun("Do this to clean up")))
                                    }

                                    it("indicates that the task has failed") {
                                        assertThat(stateMachine.taskHasFailed, equalTo(true))
                                    }
                                }
                            }
                        }
                    }
                }

                given("the task failed") {
                    val failureEvent = mock<TaskFailedEvent>()
                    val previousEvents = setOf(failureEvent)

                    beforeEachTest {
                        stateMachine.postEvent(failureEvent)
                        whenever(runStage.popNextStep(previousEvents, false)).doReturn(StageComplete)
                        whenever(cleanupStage.popNextStep(previousEvents, false)).doReturn(StepReady(mock()))
                        stateMachine.popNextStep(false)
                    }

                    given("cleanup has not failed") {
                        val otherEvent = TaskNetworkDeletedEvent
                        val events = previousEvents + otherEvent

                        beforeEachTest { stateMachine.postEvent(otherEvent) }

                        given("there are no steps still running") {
                            val stepsStillRunning = false

                            given("there are no steps ready") {
                                beforeEachTest { whenever(cleanupStage.popNextStep(events, stepsStillRunning)).doReturn(NoStepsReady) }

                                on("getting the next step to execute") {
                                    it("throws an appropriate exception") {
                                        assertThat({ stateMachine.popNextStep(stepsStillRunning) }, throws<IllegalStateException>(withMessage("None of the remaining steps are ready to execute, but there are no steps currently running.")))
                                    }

                                    it("does not provide any cleanup instructions") {
                                        assertThat(stateMachine.manualCleanupInstructions, equalTo(TextRun()))
                                    }

                                    it("indicates that the task has failed") {
                                        assertThat(stateMachine.taskHasFailed, equalTo(true))
                                    }
                                }
                            }
                        }
                    }

                    given("cleanup has failed") {
                        val otherEvent = mock<TaskFailedEvent>()
                        val events = previousEvents + otherEvent

                        beforeEachTest {
                            whenever(failureErrorMessageFormatter.formatManualCleanupMessageAfterCleanupFailure(cleanupCommands)).doReturn(TextRun("Do this to clean up"))

                            stateMachine.postEvent(otherEvent)
                        }

                        given("there are no steps still running") {
                            val stepsStillRunning = false

                            given("there are no steps ready") {
                                beforeEachTest {
                                    whenever(cleanupStage.popNextStep(events, stepsStillRunning)).doReturn(NoStepsReady)
                                }

                                on("getting the next step to execute") {
                                    val result by runNullableForEachTest { stateMachine.popNextStep(stepsStillRunning) }

                                    it("returns null") {
                                        assertThat(result, absent())
                                    }

                                    it("sets the cleanup instruction to that provided by the error message formatter") {
                                        assertThat(stateMachine.manualCleanupInstructions, equalTo(TextRun("Do this to clean up")))
                                    }

                                    it("indicates that the task has failed") {
                                        assertThat(stateMachine.taskHasFailed, equalTo(true))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        describe("getting the task exit code") {
            val mainContainer by createForEachTest { Container("main-container", imageSourceDoesNotMatter()) }
            val otherContainer by createForEachTest { Container("other-container", imageSourceDoesNotMatter()) }

            beforeEachTest {
                val node = mock<ContainerDependencyGraphNode> {
                    on { container } doReturn mainContainer
                }

                whenever(graph.taskContainerNode).thenReturn(node)
            }

            given("the task's main container exited") {
                beforeEachTest { stateMachine.postEvent(RunningContainerExitedEvent(mainContainer, 123)) }

                val exitCode by runForEachTest { stateMachine.taskExitCode }

                it("returns the exit code of the task's main container") {
                    assertThat(exitCode, equalTo(123))
                }
            }

            given("another container exited, but the main container did not") {
                beforeEachTest { stateMachine.postEvent(RunningContainerExitedEvent(otherContainer, 123)) }

                it("throws an appropriate exception") {
                    assertThat({ stateMachine.taskExitCode }, throws<IllegalStateException>(withMessage("The task has not yet finished or has failed.")))
                }
            }

            given("both the main container and another container exited") {
                beforeEachTest {
                    stateMachine.postEvent(RunningContainerExitedEvent(mainContainer, 123))
                    stateMachine.postEvent(RunningContainerExitedEvent(otherContainer, 456))
                }

                val exitCode by runForEachTest { stateMachine.taskExitCode }

                it("returns the exit code of the task's main container") {
                    assertThat(exitCode, equalTo(123))
                }
            }

            given("no containers exited") {
                it("throws an appropriate exception") {
                    assertThat({ stateMachine.taskExitCode }, throws<IllegalStateException>(withMessage("The task has not yet finished or has failed.")))
                }
            }
        }
    }
})

fun Suite.regardlessOfWhetherThereAreStepsRunning(check: Suite.(stepsStillRunning: Boolean) -> Unit) {
    mapOf(
        "there are steps still running" to true,
        "there are no steps running" to false
    ).forEach { (description, stepsStillRunning) ->
        given(description) {
            check(stepsStillRunning)
        }
    }
}
