/*
   Copyright 2017-2018 Charles Korn.

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
import batect.logging.Logger
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.TaskFailedEvent
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.events.TaskNetworkDeletedEvent
import batect.execution.model.stages.CleanupStage
import batect.execution.model.stages.CleanupStagePlanner
import batect.execution.model.stages.NoStepsReady
import batect.execution.model.stages.NoStepsRemaining
import batect.execution.model.stages.RunStage
import batect.execution.model.stages.RunStagePlanner
import batect.execution.model.stages.StepReady
import batect.execution.model.steps.TaskStep
import batect.testutils.InMemoryLogSink
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.withMessage
import batect.ui.FailureErrorMessageFormatter
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TaskStateMachineSpec : Spek({
    describe("a task state machine") {
        val graph by createForEachTest { mock<ContainerDependencyGraph>() }
        val runOptions by createForEachTest { mock<RunOptions>() }
        val logger by createForEachTest { Logger("the-source", InMemoryLogSink()) }
        val runStage by createForEachTest { mock<RunStage>() }
        val runStagePlanner by createForEachTest {
            mock<RunStagePlanner> {
                on { createStage(graph) } doReturn runStage
            }
        }

        val cleanupCommands = listOf("rm /tmp/the-file", "rm /tmp/some-other-file")
        val cleanupStage by createForEachTest {
            mock<CleanupStage> {
                on { manualCleanupCommands } doReturn cleanupCommands
            }
        }

        val cleanupStagePlanner by createForEachTest {
            mock<CleanupStagePlanner> {
                on { createStage(eq(graph), any()) } doReturn cleanupStage
            }
        }

        val failureErrorMessageFormatter by createForEachTest { mock<FailureErrorMessageFormatter>() }

        val stateMachine by createForEachTest { TaskStateMachine(graph, runOptions, runStagePlanner, cleanupStagePlanner, failureErrorMessageFormatter, logger) }

        describe("posting and retrieving events") {
            given("no events have been posted") {
                it("does not return any events") {
                    assertThat(stateMachine.getAllEvents(), isEmpty)
                }
            }

            given("an event has been posted") {
                val event = TaskNetworkDeletedEvent
                beforeEachTest { stateMachine.postEvent(event) }

                it("returns that event in the list of all events") {
                    assertThat(stateMachine.getAllEvents(), equalTo(setOf(event)))
                }
            }

            given("multiple events have been posted") {
                val events = setOf(
                    TaskNetworkDeletedEvent,
                    TaskNetworkCreatedEvent(DockerNetwork("some-network"))
                )

                beforeEachTest { events.forEach { stateMachine.postEvent(it) } }

                it("returns all posted events in the list of all events") {
                    assertThat(stateMachine.getAllEvents(), equalTo(events))
                }
            }
        }

        describe("getting the next step to execute") {
            given("the run stage is active") {
                given("the task has not failed") {
                    given("there are steps still running") {
                        val stepsStillRunning = true

                        given("there is a step ready") {
                            val step by createForEachTest { mock<TaskStep>() }
                            beforeEachTest { whenever(runStage.popNextStep(any())).doReturn(StepReady(step)) }

                            on("getting the next step to execute") {
                                val events = setOf(TaskNetworkCreatedEvent(DockerNetwork("some-network")), TaskNetworkDeletedEvent)
                                events.forEach { stateMachine.postEvent(it) }

                                val result = stateMachine.popNextStep(stepsStillRunning)

                                it("returns that step") {
                                    assertThat(result, equalTo(step))
                                }

                                it("sends all previous events to the run stage during evaluation") {
                                    verify(runStage).popNextStep(events)
                                }

                                it("does not create the cleanup stage") {
                                    verify(cleanupStagePlanner, never()).createStage(any(), any())
                                }
                            }
                        }

                        given("there are no steps ready") {
                            beforeEachTest { whenever(runStage.popNextStep(any())).doReturn(NoStepsReady) }

                            on("getting the next step to execute") {
                                val result = stateMachine.popNextStep(stepsStillRunning)

                                it("returns null") {
                                    assertThat(result, absent())
                                }

                                it("does not create the cleanup stage") {
                                    verify(cleanupStagePlanner, never()).createStage(any(), any())
                                }
                            }
                        }

                        given("there are no steps remaining") {
                            beforeEachTest { whenever(runStage.popNextStep(any())).doReturn(NoStepsRemaining) }

                            on("getting the next step to execute") {
                                val result = stateMachine.popNextStep(stepsStillRunning)

                                it("returns null") {
                                    assertThat(result, absent())
                                }

                                it("does not create the cleanup stage") {
                                    verify(cleanupStagePlanner, never()).createStage(any(), any())
                                }
                            }
                        }
                    }

                    given("there are no steps still running") {
                        val stepsStillRunning = false

                        given("there is a step ready") {
                            val step by createForEachTest { mock<TaskStep>() }
                            beforeEachTest { whenever(runStage.popNextStep(any())).doReturn(StepReady(step)) }

                            on("getting the next step to execute") {
                                val events = setOf(TaskNetworkCreatedEvent(DockerNetwork("some-network")), TaskNetworkDeletedEvent)
                                events.forEach { stateMachine.postEvent(it) }

                                val result = stateMachine.popNextStep(stepsStillRunning)

                                it("returns that step") {
                                    assertThat(result, equalTo(step))
                                }

                                it("sends all previous events to the run stage during evaluation") {
                                    verify(runStage).popNextStep(events)
                                }

                                it("does not create the cleanup stage") {
                                    verify(cleanupStagePlanner, never()).createStage(any(), any())
                                }
                            }
                        }

                        given("there are no steps ready") {
                            beforeEachTest { whenever(runStage.popNextStep(any())).doReturn(NoStepsReady) }

                            on("getting the next step to execute") {
                                it("throws an appropriate exception") {
                                    assertThat({ stateMachine.popNextStep(stepsStillRunning) }, throws<IllegalStateException>(withMessage("None of the remaining steps are ready to execute, but there are no steps currently running.")))
                                }
                            }
                        }

                        given("there are no steps remaining") {
                            val event = TaskNetworkCreatedEvent(DockerNetwork("some-network"))

                            beforeEachTest {
                                val step = mock<TaskStep>()

                                whenever(runStage.popNextStep(emptySet())).doReturn(StepReady(step))
                                whenever(runStage.popNextStep(setOf(event))).doReturn(NoStepsRemaining)

                                val firstStep = stateMachine.popNextStep(stepsStillRunning)
                                assertThat(firstStep, equalTo(step))
                                stateMachine.postEvent(event)
                            }

                            on("getting the next step to execute") {
                                val cleanupStep = mock<TaskStep>()
                                whenever(cleanupStage.popNextStep(setOf(event))).doReturn(StepReady(cleanupStep))

                                val result = stateMachine.popNextStep(stepsStillRunning)

                                it("returns the first step from the cleanup stage") {
                                    assertThat(result, equalTo(cleanupStep))
                                }

                                it("sends all previous events to the cleanup stage planner") {
                                    verify(cleanupStagePlanner).createStage(graph, setOf(event))
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
                            val result = stateMachine.popNextStep(stepsStillRunning)

                            it("returns null") {
                                assertThat(result, absent())
                            }

                            it("does not pop any steps from the run stage") {
                                verify(runStage, never()).popNextStep(any())
                            }

                            it("does not create the cleanup stage") {
                                verify(cleanupStagePlanner, never()).createStage(any(), any())
                            }
                        }
                    }

                    given("there are no steps still running") {
                        val stepsStillRunning = false

                        given("cleanup after failure is enabled") {
                            beforeEachTest { whenever(runOptions.behaviourAfterFailure) doReturn BehaviourAfterFailure.Cleanup }

                            on("getting the next steps to execute") {
                                val firstCleanupStep = mock<TaskStep>()
                                val secondCleanupStep = mock<TaskStep>()
                                whenever(cleanupStage.popNextStep(setOf(failureEvent))).doReturn(StepReady(firstCleanupStep), StepReady(secondCleanupStep))

                                val firstResult = stateMachine.popNextStep(stepsStillRunning)
                                val secondResult = stateMachine.popNextStep(stepsStillRunning)

                                it("returns the first step from the cleanup stage on the first call") {
                                    assertThat(firstResult, equalTo(firstCleanupStep))
                                }

                                it("returns the second step from the cleanup stage on the second call") {
                                    assertThat(secondResult, equalTo(secondCleanupStep))
                                }

                                it("does not pop any steps from the run stage") {
                                    verify(runStage, never()).popNextStep(any())
                                }

                                it("sends all previous events to the cleanup stage planner, and only creates the cleanup stage once") {
                                    verify(cleanupStagePlanner, times(1)).createStage(graph, setOf(failureEvent))
                                }
                            }
                        }

                        given("cleanup after failure is disabled") {
                            beforeEachTest { whenever(runOptions.behaviourAfterFailure) doReturn BehaviourAfterFailure.DontCleanup }

                            given("no containers have been created") {
                                on("getting the next steps to execute") {
                                    val firstCleanupStep = mock<TaskStep>()
                                    val secondCleanupStep = mock<TaskStep>()
                                    whenever(cleanupStage.popNextStep(setOf(failureEvent))).doReturn(StepReady(firstCleanupStep), StepReady(secondCleanupStep))

                                    val firstResult = stateMachine.popNextStep(stepsStillRunning)
                                    val secondResult = stateMachine.popNextStep(stepsStillRunning)

                                    it("returns the first step from the cleanup stage on the first call") {
                                        assertThat(firstResult, equalTo(firstCleanupStep))
                                    }

                                    it("returns the second step from the cleanup stage on the second call") {
                                        assertThat(secondResult, equalTo(secondCleanupStep))
                                    }

                                    it("does not pop any steps from the run stage") {
                                        verify(runStage, never()).popNextStep(any())
                                    }

                                    it("sends all previous events to the cleanup stage planner, and only creates the cleanup stage once") {
                                        verify(cleanupStagePlanner, times(1)).createStage(graph, setOf(failureEvent))
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

                                    whenever(failureErrorMessageFormatter.formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(events, cleanupCommands)).doReturn("Do this to clean up")
                                }

                                on("getting the next steps to execute") {
                                    val cleanupStepThatShouldNeverBeRun = mock<TaskStep>()
                                    whenever(cleanupStage.popNextStep(events)).doReturn(StepReady(cleanupStepThatShouldNeverBeRun))

                                    val result = stateMachine.popNextStep(stepsStillRunning)

                                    it("returns null") {
                                        assertThat(result, absent())
                                    }

                                    it("does not pop any steps from the run stage") {
                                        verify(runStage, never()).popNextStep(any())
                                    }

                                    it("sends all previous events to the cleanup stage planner") {
                                        verify(cleanupStagePlanner).createStage(graph, events)
                                    }

                                    it("sets the cleanup instruction to that provided by the error message formatter") {
                                        assertThat(stateMachine.manualCleanupInstructions, equalTo("Do this to clean up"))
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
                        whenever(runStage.popNextStep(emptySet())).doReturn(NoStepsRemaining)
                        whenever(cleanupStage.popNextStep(emptySet())).doReturn(StepReady(mock()))
                        stateMachine.popNextStep(false)

                        previousEvents.forEach { stateMachine.postEvent(it) }
                    }

                    given("cleanup has not failed") {
                        given("there are steps still running") {
                            val stepsStillRunning = true

                            given("there is a step ready") {
                                val step by createForEachTest { mock<TaskStep>() }
                                beforeEachTest { whenever(cleanupStage.popNextStep(previousEvents)).doReturn(StepReady(step)) }

                                on("getting the next step to execute") {
                                    val result = stateMachine.popNextStep(stepsStillRunning)

                                    it("returns that step") {
                                        assertThat(result, equalTo(step))
                                    }
                                }
                            }

                            given("there are no steps ready") {
                                beforeEachTest { whenever(cleanupStage.popNextStep(previousEvents)).doReturn(NoStepsReady) }

                                on("getting the next step to execute") {
                                    val result = stateMachine.popNextStep(stepsStillRunning)

                                    it("returns null") {
                                        assertThat(result, absent())
                                    }
                                }
                            }

                            given("there are no steps remaining") {
                                beforeEachTest { whenever(cleanupStage.popNextStep(previousEvents)).doReturn(NoStepsRemaining) }

                                on("getting the next step to execute") {
                                    val result = stateMachine.popNextStep(stepsStillRunning)

                                    it("returns null") {
                                        assertThat(result, absent())
                                    }
                                }
                            }
                        }

                        given("there are no steps still running") {
                            val stepsStillRunning = false

                            given("there is a step ready") {
                                val step by createForEachTest { mock<TaskStep>() }
                                beforeEachTest { whenever(cleanupStage.popNextStep(previousEvents)).doReturn(StepReady(step)) }

                                on("getting the next step to execute") {
                                    val result = stateMachine.popNextStep(stepsStillRunning)

                                    it("returns that step") {
                                        assertThat(result, equalTo(step))
                                    }
                                }
                            }

                            given("there are no steps ready") {
                                beforeEachTest { whenever(cleanupStage.popNextStep(previousEvents)).doReturn(NoStepsReady) }

                                on("getting the next step to execute") {
                                    it("throws an appropriate exception") {
                                        assertThat({ stateMachine.popNextStep(stepsStillRunning) }, throws<IllegalStateException>(withMessage("None of the remaining steps are ready to execute, but there are no steps currently running.")))
                                    }

                                    it("does not provide any cleanup instructions") {
                                        assertThat(stateMachine.manualCleanupInstructions, equalTo(""))
                                    }

                                    it("indicates that the task has failed") {
                                        assertThat(stateMachine.taskHasFailed, equalTo(true))
                                    }
                                }
                            }

                            given("there are no steps remaining") {
                                beforeEachTest { whenever(cleanupStage.popNextStep(previousEvents)).doReturn(NoStepsRemaining) }

                                on("getting the next step to execute") {
                                    val result = stateMachine.popNextStep(stepsStillRunning)

                                    it("returns null") {
                                        assertThat(result, absent())
                                    }

                                    it("does not provide any cleanup instructions") {
                                        assertThat(stateMachine.manualCleanupInstructions, equalTo(""))
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
                            whenever(failureErrorMessageFormatter.formatManualCleanupMessageAfterCleanupFailure(cleanupCommands)).doReturn("Do this to clean up")

                            stateMachine.postEvent(event)
                        }

                        given("there are steps still running") {
                            val stepsStillRunning = true

                            given("there is a step ready") {
                                val step by createForEachTest { mock<TaskStep>() }
                                beforeEachTest { whenever(cleanupStage.popNextStep(previousEventsWithFailureEvent)).doReturn(StepReady(step)) }

                                on("getting the next step to execute") {
                                    val result = stateMachine.popNextStep(stepsStillRunning)

                                    it("returns that step") {
                                        assertThat(result, equalTo(step))
                                    }
                                }
                            }

                            given("there are no steps ready") {
                                beforeEachTest { whenever(cleanupStage.popNextStep(previousEventsWithFailureEvent)).doReturn(NoStepsReady) }

                                on("getting the next step to execute") {
                                    val result = stateMachine.popNextStep(stepsStillRunning)

                                    it("returns null") {
                                        assertThat(result, absent())
                                    }
                                }
                            }

                            given("there are no steps remaining") {
                                beforeEachTest { whenever(cleanupStage.popNextStep(previousEventsWithFailureEvent)).doReturn(NoStepsRemaining) }

                                on("getting the next step to execute") {
                                    val result = stateMachine.popNextStep(stepsStillRunning)

                                    it("returns null") {
                                        assertThat(result, absent())
                                    }
                                }
                            }
                        }

                        given("there are no steps still running") {
                            val stepsStillRunning = false

                            given("there is a step ready") {
                                val step by createForEachTest { mock<TaskStep>() }
                                beforeEachTest { whenever(cleanupStage.popNextStep(previousEventsWithFailureEvent)).doReturn(StepReady(step)) }

                                on("getting the next step to execute") {
                                    val result = stateMachine.popNextStep(stepsStillRunning)

                                    it("returns that step") {
                                        assertThat(result, equalTo(step))
                                    }
                                }
                            }

                            given("there are no steps ready") {
                                beforeEachTest {
                                    whenever(cleanupStage.popNextStep(previousEventsWithFailureEvent)).doReturn(NoStepsReady)
                                }

                                on("getting the next step to execute") {
                                    val result = stateMachine.popNextStep(stepsStillRunning)

                                    it("returns null") {
                                        assertThat(result, absent())
                                    }

                                    it("sets the cleanup instruction to that provided by the error message formatter") {
                                        assertThat(stateMachine.manualCleanupInstructions, equalTo("Do this to clean up"))
                                    }
                                }
                            }

                            given("there are no steps remaining") {
                                beforeEachTest {
                                    whenever(cleanupStage.popNextStep(previousEventsWithFailureEvent)).doReturn(NoStepsRemaining)
                                }

                                on("getting the next step to execute") {
                                    val result = stateMachine.popNextStep(stepsStillRunning)

                                    it("returns null") {
                                        assertThat(result, absent())
                                    }

                                    it("sets the cleanup instruction to that provided by the error message formatter") {
                                        assertThat(stateMachine.manualCleanupInstructions, equalTo("Do this to clean up"))
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
                        whenever(runStage.popNextStep(previousEvents)).doReturn(NoStepsRemaining)
                        whenever(cleanupStage.popNextStep(previousEvents)).doReturn(StepReady(mock()))
                        stateMachine.popNextStep(false)
                    }

                    given("cleanup has not failed") {
                        val otherEvent = TaskNetworkDeletedEvent
                        val events = previousEvents + otherEvent

                        beforeEachTest { stateMachine.postEvent(otherEvent) }

                        given("there are no steps still running") {
                            val stepsStillRunning = false

                            given("there are no steps ready") {
                                beforeEachTest { whenever(cleanupStage.popNextStep(events)).doReturn(NoStepsReady) }

                                on("getting the next step to execute") {
                                    it("throws an appropriate exception") {
                                        assertThat({ stateMachine.popNextStep(stepsStillRunning) }, throws<IllegalStateException>(withMessage("None of the remaining steps are ready to execute, but there are no steps currently running.")))
                                    }

                                    it("does not provide any cleanup instructions") {
                                        assertThat(stateMachine.manualCleanupInstructions, equalTo(""))
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
                            whenever(failureErrorMessageFormatter.formatManualCleanupMessageAfterCleanupFailure(cleanupCommands)).doReturn("Do this to clean up")

                            stateMachine.postEvent(otherEvent)
                        }

                        given("there are no steps still running") {
                            val stepsStillRunning = false

                            given("there are no steps ready") {
                                beforeEachTest {
                                    whenever(cleanupStage.popNextStep(events)).doReturn(NoStepsReady)
                                }

                                on("getting the next step to execute") {
                                    val result = stateMachine.popNextStep(stepsStillRunning)

                                    it("returns null") {
                                        assertThat(result, absent())
                                    }

                                    it("sets the cleanup instruction to that provided by the error message formatter") {
                                        assertThat(stateMachine.manualCleanupInstructions, equalTo("Do this to clean up"))
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
    }
})
