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

package batect.model

import batect.config.Configuration
import batect.config.Container
import batect.config.ContainerMap
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.logging.Logger
import batect.logging.LoggerFactory
import batect.model.events.ContainerBecameHealthyEvent
import batect.model.events.TaskEvent
import batect.model.events.TaskEventContext
import batect.model.events.TaskStartedEvent
import batect.model.steps.BeginTaskStep
import batect.model.steps.CreateTaskNetworkStep
import batect.model.steps.DeleteTaskNetworkStep
import batect.model.steps.DisplayTaskFailureStep
import batect.model.steps.TaskStep
import batect.os.Command
import batect.testutils.InMemoryLogSink
import batect.testutils.createForEachTest
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.withMessage
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TaskStateMachineSpec : Spek({
    describe("a task state machine") {
        val dependencyContainer1 = Container("dependency-container-1", imageSourceDoesNotMatter())

        val dependencyContainer2 = Container("dependency-container-2", imageSourceDoesNotMatter(),
            dependencies = setOf(dependencyContainer1.name),
            command = Command.parse("do-stuff-in-container-2"))

        val taskContainer = Container("some-container", imageSourceDoesNotMatter(),
            dependencies = setOf(dependencyContainer2.name),
            command = Command.parse("do-stuff-in-task-container"))

        val unrelatedContainer = Container("some-other-container", imageSourceDoesNotMatter())
        val runConfig = TaskRunConfiguration(taskContainer.name, Command.parse("some-command"), mapOf("SOME_VAR" to "some value"))
        val task = Task("the-task", runConfig)
        val config = Configuration("the-project", TaskMap(task), ContainerMap(taskContainer, dependencyContainer1, dependencyContainer2, unrelatedContainer))
        val commandResolver = mock<ContainerCommandResolver> {
            on { resolveCommand(any(), any()) } doReturn Command.parse("the-resolved-command")
        }

        val graph = DependencyGraph(config, task, commandResolver)
        val loggerCreatedByFactory = mock<Logger>()
        val loggerFactory = mock<LoggerFactory> {
            on { createLoggerForClass(any()) } doReturn loggerCreatedByFactory
        }

        val stateMachine by createForEachTest {
            val logger = Logger("stateMachine", InMemoryLogSink())
            TaskStateMachine(graph, BehaviourAfterFailure.DontCleanup, logger, loggerFactory)
        }

        describe("queuing, popping and querying steps") {
            on("when no steps have been queued") {
                val step = stateMachine.popNextStep()

                it("does not return a step when popping the next step") {
                    assertThat(step, absent())
                }

                it("returns an empty list of pending and processed steps") {
                    assertThat(stateMachine.getPendingAndProcessedStepsOfType(TaskStep::class), isEmpty)
                }

                it("returns an empty list of processed steps") {
                    assertThat(stateMachine.getProcessedStepsOfType(TaskStep::class), isEmpty)
                }
            }

            describe("when one step has been queued") {
                beforeEachTest {
                    stateMachine.queueStep(BeginTaskStep)
                }

                on("popping next steps") {
                    val firstStep = stateMachine.popNextStep()
                    val secondStep = stateMachine.popNextStep()

                    it("returns the queued step on the first pop") {
                        assertThat(firstStep, equalTo<TaskStep>(BeginTaskStep))
                    }

                    it("does not return a step on subsequent pops") {
                        assertThat(secondStep, absent())
                    }
                }

                describe("querying for pending and processed steps") {
                    on("when the step has not been popped yet") {
                        it("returns the step when asked for a list of pending and processed steps using the base class name") {
                            assertThat(stateMachine.getPendingAndProcessedStepsOfType(TaskStep::class), equalTo(setOf<TaskStep>(BeginTaskStep)))
                        }

                        it("returns the step when asked for a list of pending and processed steps using the derived class name") {
                            assertThat(stateMachine.getPendingAndProcessedStepsOfType(BeginTaskStep::class), equalTo(setOf(BeginTaskStep)))
                        }

                        it("returns an empty list when asked for a list of pending and processed steps of a different step type") {
                            assertThat(stateMachine.getPendingAndProcessedStepsOfType(DeleteTaskNetworkStep::class), isEmpty)
                        }

                        it("returns an empty list of processed steps") {
                            assertThat(stateMachine.getProcessedStepsOfType(TaskStep::class), isEmpty)
                        }
                    }

                    on("when the step has been popped") {
                        stateMachine.popNextStep()

                        it("returns the step when asked for a list of pending and processed steps using the base class name") {
                            assertThat(stateMachine.getPendingAndProcessedStepsOfType(TaskStep::class), equalTo(setOf<TaskStep>(BeginTaskStep)))
                        }

                        it("returns the step when asked for a list of pending and processed steps using the derived class name") {
                            assertThat(stateMachine.getPendingAndProcessedStepsOfType(BeginTaskStep::class), equalTo(setOf(BeginTaskStep)))
                        }

                        it("returns an empty list when asked for a list of pending and processed steps of a different step type") {
                            assertThat(stateMachine.getPendingAndProcessedStepsOfType(DeleteTaskNetworkStep::class), isEmpty)
                        }

                        it("returns the step when asked for a list of processed steps using the base class name") {
                            assertThat(stateMachine.getProcessedStepsOfType(TaskStep::class), equalTo(setOf<TaskStep>(BeginTaskStep)))
                        }

                        it("returns the step when asked for a list of processed steps using the derived class name") {
                            assertThat(stateMachine.getProcessedStepsOfType(BeginTaskStep::class), equalTo(setOf(BeginTaskStep)))
                        }

                        it("returns an empty list when asked for a list of processed steps of a different step type") {
                            assertThat(stateMachine.getProcessedStepsOfType(DeleteTaskNetworkStep::class), isEmpty)
                        }
                    }
                }
            }

            describe("when multiple steps have been queued") {
                val step1 = DisplayTaskFailureStep("Something went wrong")
                val step2 = DisplayTaskFailureStep("Something else went wrong")
                val step3 = DisplayTaskFailureStep("A third thing went wrong")

                beforeEachTest {
                    stateMachine.queueStep(step1)
                    stateMachine.queueStep(step2)
                    stateMachine.queueStep(step3)
                }

                on("popping each of them") {
                    val firstStepPopped = stateMachine.popNextStep()
                    val secondStepPopped = stateMachine.popNextStep()
                    val thirdStepPopped = stateMachine.popNextStep()

                    it("returns them in the order they were queued") {
                        assertThat(firstStepPopped, equalTo<TaskStep>(step1))
                        assertThat(secondStepPopped, equalTo<TaskStep>(step2))
                        assertThat(thirdStepPopped, equalTo<TaskStep>(step3))
                    }
                }
            }
        }

        describe("removing pending steps") {
            on("when there are no steps queued") {
                stateMachine.removePendingStepsOfType(CreateTaskNetworkStep::class)

                it("still returns no pending or processed steps of that type") {
                    assertThat(stateMachine.getPendingAndProcessedStepsOfType(CreateTaskNetworkStep::class), isEmpty)
                }
            }

            describe("when there is a single step queued") {
                beforeEachTest {
                    stateMachine.queueStep(CreateTaskNetworkStep)
                }

                on("removing pending steps of that step's type") {
                    stateMachine.removePendingStepsOfType(CreateTaskNetworkStep::class)

                    it("returns no pending or processed steps of that type") {
                        assertThat(stateMachine.getPendingAndProcessedStepsOfType(CreateTaskNetworkStep::class), isEmpty)
                    }
                }

                on("removing pending steps of another type") {
                    stateMachine.removePendingStepsOfType(DeleteTaskNetworkStep::class)

                    it("returns that step in the list of pending and processed steps") {
                        assertThat(stateMachine.getPendingAndProcessedStepsOfType(CreateTaskNetworkStep::class),
                            equalTo(setOf(CreateTaskNetworkStep)))
                    }
                }

                describe("when the step has been processed") {
                    beforeEachTest {
                        stateMachine.popNextStep()
                    }

                    on("removing pending steps of that step's type") {
                        stateMachine.removePendingStepsOfType(CreateTaskNetworkStep::class)

                        it("returns that step in the list of pending and processed steps") {
                            assertThat(stateMachine.getPendingAndProcessedStepsOfType(CreateTaskNetworkStep::class),
                                equalTo(setOf(CreateTaskNetworkStep)))
                        }

                        it("returns that step in the list of processed steps") {
                            assertThat(stateMachine.getProcessedStepsOfType(CreateTaskNetworkStep::class),
                                equalTo(setOf(CreateTaskNetworkStep)))
                        }
                    }

                    on("removing pending steps of another type") {
                        stateMachine.removePendingStepsOfType(DeleteTaskNetworkStep::class)

                        it("returns that step in the list of pending and processed steps") {
                            assertThat(stateMachine.getPendingAndProcessedStepsOfType(CreateTaskNetworkStep::class),
                                equalTo(setOf(CreateTaskNetworkStep)))
                        }
                    }
                }
            }

            describe("when there are multiple steps of the same type queued") {
                val step1 = DisplayTaskFailureStep("Something went wrong")
                val step2 = DisplayTaskFailureStep("Something else went wrong")
                val step3 = DisplayTaskFailureStep("A third thing went wrong")

                beforeEachTest {
                    stateMachine.queueStep(step1)
                    stateMachine.queueStep(step2)
                    stateMachine.queueStep(step3)
                    stateMachine.queueStep(CreateTaskNetworkStep)
                }

                on("removing pending steps of that step's type") {
                    stateMachine.removePendingStepsOfType(DisplayTaskFailureStep::class)

                    it("returns no pending or processed steps of that type") {
                        assertThat(stateMachine.getPendingAndProcessedStepsOfType(DisplayTaskFailureStep::class), isEmpty)
                    }

                    it("returns pending or processed steps of other types") {
                        assertThat(stateMachine.getPendingAndProcessedStepsOfType(CreateTaskNetworkStep::class),
                            equalTo(setOf(CreateTaskNetworkStep)))
                    }
                }

                on("removing pending steps of another type") {
                    stateMachine.removePendingStepsOfType(DeleteTaskNetworkStep::class)

                    it("returns the steps in the list of pending and processed steps") {
                        assertThat(stateMachine.getPendingAndProcessedStepsOfType(DisplayTaskFailureStep::class),
                            equalTo(setOf(step1, step2, step3)))
                    }
                }

                describe("after one of the steps has been processed") {
                    beforeEachTest {
                        stateMachine.popNextStep()
                    }

                    on("removing pending steps of that step's type") {
                        stateMachine.removePendingStepsOfType(DisplayTaskFailureStep::class)

                        it("returns only the processed step when asked for pending or processed steps of that type") {
                            assertThat(stateMachine.getPendingAndProcessedStepsOfType(DisplayTaskFailureStep::class),
                                equalTo(setOf(step1)))
                        }

                        it("returns only the processed step when asked for processed steps of that type") {
                            assertThat(stateMachine.getProcessedStepsOfType(DisplayTaskFailureStep::class),
                                equalTo(setOf(step1)))
                        }
                    }
                }
            }
        }

        describe("aborting the task") {
            on("when the task has not been aborted") {
                it("indicates that the task is not aborting") {
                    assertThat(stateMachine.isAborting, equalTo(false))
                }
            }

            on("when the task has been aborted") {
                stateMachine.abort()

                it("indicates that the task is aborting") {
                    assertThat(stateMachine.isAborting, equalTo(true))
                }
            }
        }

        describe("posting events") {
            on("receiving an event") {
                var sawSelfInEventList = false
                val event = mock<TaskEvent> {
                    on { apply(any(), any()) } doAnswer { invocation ->
                        val appliedToStateMachine = invocation.getArgument<TaskStateMachine>(0)
                        sawSelfInEventList = appliedToStateMachine.getPastEventsOfType(TaskEvent::class).contains(invocation.mock)

                        null
                    }
                }

                stateMachine.postEvent(event)

                it("applies the event") {
                    verify(event).apply(stateMachine, loggerCreatedByFactory)
                }

                it("adds the event to the list of past events before applying it") {
                    assertThat(sawSelfInEventList, equalTo(true))
                }
            }
        }

        describe("getting past events") {
            data class HarmlessTestEvent(val someAttribute: String) : TaskEvent() {
                override fun apply(context: TaskEventContext, logger: Logger) {
                    // Do nothing.
                }
            }

            val testEvent1 = HarmlessTestEvent("some value")
            val testEvent2 = HarmlessTestEvent("some other value")
            val taskStartedEvent = TaskStartedEvent

            beforeEachTest {
                stateMachine.postEvent(testEvent1)
                stateMachine.postEvent(testEvent2)
                stateMachine.postEvent(taskStartedEvent)
            }

            describe("getting all past events for an event type") {
                on("when that event type has no past events") {
                    it("returns an empty list") {
                        assertThat(stateMachine.getPastEventsOfType(ContainerBecameHealthyEvent::class), isEmpty)
                    }
                }

                on("when that event type has one past event") {
                    it("returns just that event") {
                        assertThat(stateMachine.getPastEventsOfType(TaskStartedEvent::class), equalTo(setOf(taskStartedEvent)))
                    }
                }

                on("when that event type has multiple past events") {
                    it("returns all past events of that type") {
                        assertThat(stateMachine.getPastEventsOfType(HarmlessTestEvent::class),
                            equalTo(setOf(testEvent1, testEvent2)))
                    }
                }
            }

            describe("getting the single past event for an event type") {
                on("when that event type has no past events") {
                    it("returns null") {
                        assertThat(stateMachine.getSinglePastEventOfType(ContainerBecameHealthyEvent::class), absent())
                    }
                }

                on("when that event type has one past event") {
                    it("returns just that event") {
                        assertThat(stateMachine.getSinglePastEventOfType(TaskStartedEvent::class), equalTo(taskStartedEvent))
                    }
                }

                on("when that event type has multiple past events") {
                    it("throws an exception") {
                        assertThat({ stateMachine.getSinglePastEventOfType(HarmlessTestEvent::class) },
                            throws<IllegalStateException>(withMessage("Multiple events of type HarmlessTestEvent found.")))
                    }
                }
            }
        }

        on("getting the command for a container") {
            it("returns the command from the container's dependency graph node") {
                assertThat(stateMachine.commandForContainer(dependencyContainer1), equalTo(graph.nodeFor(dependencyContainer1).command))
            }
        }

        describe("getting additional environment variables for a container") {
            on("when the container is not the task container") {
                it("returns an empty set of additional environment variables") {
                    assertThat(stateMachine.additionalEnvironmentVariablesForContainer(dependencyContainer1), equalTo(emptyMap()))
                }
            }

            on("when the container is the task container") {
                it("returns the set of additional environment variables from the task run configuration") {
                    assertThat(stateMachine.additionalEnvironmentVariablesForContainer(taskContainer), equalTo(runConfig.additionalEnvironmentVariables))
                }
            }
        }

        describe("determining if a container is the task container") {
            on("querying with the task container") {
                it("returns true") {
                    assertThat(stateMachine.isTaskContainer(taskContainer), equalTo(true))
                }
            }

            on("querying with a dependency container") {
                it("returns false") {
                    assertThat(stateMachine.isTaskContainer(dependencyContainer1), equalTo(false))
                }
            }
        }

        describe("getting dependencies of a container") {
            on("querying for the task container") {
                it("returns all direct dependencies of the task container") {
                    assertThat(stateMachine.dependenciesOf(taskContainer), equalTo(setOf(dependencyContainer2)))
                }
            }

            on("querying for another container with dependencies") {
                it("returns all direct dependencies of that container") {
                    assertThat(stateMachine.dependenciesOf(dependencyContainer2), equalTo(setOf(dependencyContainer1)))
                }
            }

            on("querying for another container with no dependencies") {
                it("returns an empty set") {
                    assertThat(stateMachine.dependenciesOf(dependencyContainer1), isEmpty)
                }
            }
        }

        describe("getting containers that depend on a container") {
            on("querying for the task container") {
                it("returns an empty list") {
                    assertThat(stateMachine.containersThatDependOn(taskContainer), isEmpty)
                }
            }

            on("querying for any other container") {
                it("returns all containers that directly depend on that container") {
                    assertThat(stateMachine.containersThatDependOn(dependencyContainer2), equalTo(setOf(taskContainer)))
                }
            }
        }

        on("getting all containers for the task") {
            it("returns a set of all containers related to the task") {
                assertThat(stateMachine.allTaskContainers, equalTo(setOf(dependencyContainer1, dependencyContainer2, taskContainer)))
            }
        }

        on("getting the project name") {
            it("returns the project name") {
                assertThat(stateMachine.projectName, equalTo("the-project"))
            }
        }
    }
})
