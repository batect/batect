package decompose

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import decompose.config.Configuration
import decompose.config.Container
import decompose.config.ContainerMap
import decompose.config.Task
import decompose.config.TaskMap
import decompose.config.TaskRunConfiguration
import decompose.model.DependencyGraph
import decompose.model.DependencyGraphProvider
import decompose.model.TaskStateMachine
import decompose.model.TaskStateMachineProvider
import decompose.model.events.TaskEvent
import decompose.model.events.TaskEventSink
import decompose.model.steps.BuildImageStep
import decompose.model.steps.CreateTaskNetworkStep
import decompose.model.steps.FinishTaskStep
import decompose.model.steps.TaskStepRunner
import decompose.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TaskRunnerSpec : Spek({
    describe("a task runner") {
        val eventLogger = mock<EventLogger>()
        val taskStepRunner = mock<TaskStepRunner>()
        val graph = mock<DependencyGraph>()
        val graphProvider = mock<DependencyGraphProvider>()
        val stateMachine = mock<TaskStateMachine>()
        val stateMachineProvider = mock<TaskStateMachineProvider> {
            on { createStateMachine(graph) } doReturn stateMachine
        }

        val taskRunner = TaskRunner(eventLogger, taskStepRunner, graphProvider, stateMachineProvider)

        beforeEachTest {
            reset(taskStepRunner)
            reset(eventLogger)
            reset(stateMachine)
        }

        on("attempting to run a task that does not exist") {
            val config = Configuration("some-project", TaskMap(), ContainerMap())

            it("throws an appropriate exception") {
                assertThat({ taskRunner.run(config, "some-task") }, throws<ExecutionException>(withMessage("The task 'some-task' does not exist.")))
            }
        }

        describe("running a task that exists") {
            val container = Container("some-container", "/some-build-dir")
            val runConfiguration = TaskRunConfiguration(container.name)
            val task = Task("some-task", runConfiguration)
            val config = Configuration("some-project", TaskMap(task), ContainerMap(container))

            beforeEachTest {
                whenever(graphProvider.createGraph(config, task)).thenReturn(graph)
            }

            on("the task finishing with no 'finish task' step") {
                val exitCode = taskRunner.run(config, task.name)

                it("logs that the task failed") {
                    verify(eventLogger).taskFailed("some-task")
                }

                it("returns a non-zero exit code") {
                    assertThat(exitCode, !equalTo(0))
                }
            }

            on("the task finishing with a 'finish task' step") {
                val finishTaskStep = FinishTaskStep(123)
                whenever(stateMachine.popNextStep()).thenReturn(finishTaskStep, null)

                val exitCode = taskRunner.run(config, task.name)

                it("logs it to the event logger") {
                    verify(eventLogger).logBeforeStartingStep(finishTaskStep)
                }

                it("runs the step") {
                    verify(taskStepRunner).run(eq(finishTaskStep), any())
                }

                it("returns the exit code from the 'finish task' step") {
                    assertThat(exitCode, equalTo(123))
                }
            }

            on("the state machine producing a step to run") {
                val step = BuildImageStep(config.projectName, container)
                val eventToPost = mock<TaskEvent>()

                whenever(stateMachine.popNextStep()).thenReturn(step, null)
                whenever(taskStepRunner.run(eq(step), any())).then { invocation ->
                    val eventSink = invocation.arguments[1] as TaskEventSink
                    eventSink.postEvent(eventToPost)

                    null
                }

                taskRunner.run(config, task.name)

                it("logs it to the event logger") {
                    verify(eventLogger).logBeforeStartingStep(step)
                }

                it("runs the step") {
                    verify(taskStepRunner).run(eq(step), any())
                }

                it("logs it to the event logger and then runs it") {
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

            on("the state machine producing multiple steps to run") {
                val step1 = BuildImageStep(config.projectName, container)
                val step2 = CreateTaskNetworkStep
                whenever(stateMachine.popNextStep()).thenReturn(step1, step2, null)

                taskRunner.run(config, task.name)

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

                it("performs each operation in the correct order") {
                    inOrder(eventLogger, taskStepRunner) {
                        verify(eventLogger).logBeforeStartingStep(step1)
                        verify(taskStepRunner).run(eq(step1), any())
                        verify(eventLogger).logBeforeStartingStep(step2)
                        verify(taskStepRunner).run(eq(step2), any())
                    }
                }
            }
        }
    }
})
