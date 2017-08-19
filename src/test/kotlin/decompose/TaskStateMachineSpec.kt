package decompose

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import decompose.config.Configuration
import decompose.config.Container
import decompose.config.ContainerMap
import decompose.config.Task
import decompose.config.TaskMap
import decompose.config.TaskRunConfiguration
import decompose.docker.DockerContainer
import decompose.docker.DockerImage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.SpecBody
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

object TaskStateMachineSpec : Spek({
    describe("a task state machine") {
        given("a task with no dependencies") {
            val container = Container("some-container", "/build-dir")
            val runConfig = TaskRunConfiguration(container.name, "some-command")
            val task = Task("the-task", runConfig)
            val config = Configuration("the-project", TaskMap(task), ContainerMap(container))
            val graph = DependencyGraph(config, task)
            val stateMachine by CreateForEachTest(this, { TaskStateMachine(graph) })

            describe("initial state") {
                on("getting the next step") {
                    val nextStep = stateMachine.popNextStep()

                    it("gives the next step as starting the task") {
                        assert.that(nextStep, equalTo<TaskStep>(BeginTaskStep))
                    }

                    it("does not return any further steps") {
                        assert.that(stateMachine.popNextStep(), absent())
                    }
                }
            }

            describe("after processing the 'begin task' step") {
                beforeEachTest {
                    assert.that(stateMachine.popNextStep(), equalTo<TaskStep>(BeginTaskStep))
                }

                on("receiving a 'task started' event") {
                    stateMachine.processEvent(TaskStartedEvent)

                    it("gives the next step as building the image for the task container") {
                        assert.that(stateMachine.popNextStep(), equalTo<TaskStep>(BuildImageStep(container)))
                    }

                    it("does not return any further steps") {
                        assert.that(stateMachine.popNextStep(), absent())
                    }

                }
            }

            describe("after processing the 'build image' step") {
                beforeEachTest {
                    assert.that(stateMachine.popNextStep(), equalTo<TaskStep>(BeginTaskStep))
                    stateMachine.processEvent(TaskStartedEvent)
                    assert.that(stateMachine.popNextStep() as TaskStep, isA<BuildImageStep>())
                }

                on("receiving a 'image built' event") {
                    val image = DockerImage("some-image-id")
                    stateMachine.processEvent(ImageBuiltEvent(container, image))

                    it("gives the next step as creating the container for the task container") {
                        assert.that(stateMachine.popNextStep(), equalTo<TaskStep>(CreateContainerStep(container, image)))
                    }

                    it("does not return any further steps") {
                        assert.that(stateMachine.popNextStep(), absent())
                    }
                }

                on("receiving a 'image build failed' event") {
                    stateMachine.processEvent(ImageBuildFailedEvent(container, "Something went wrong"))

                    it("gives the next step as displaying an error to the user") {
                        assert.that(stateMachine.popNextStep(), equalTo<TaskStep>(DisplayTaskFailureStep("Could not build image for container 'some-container': Something went wrong")))
                    }

                    it("does not return any further steps") {
                        assert.that(stateMachine.popNextStep(), absent())
                    }
                }
            }

            describe("after processing the 'create container' step") {
                beforeEachTest {
                    assert.that(stateMachine.popNextStep(), equalTo<TaskStep>(BeginTaskStep))
                    stateMachine.processEvent(TaskStartedEvent)
                    assert.that(stateMachine.popNextStep() as TaskStep, isA<BuildImageStep>())
                    stateMachine.processEvent(ImageBuiltEvent(container, DockerImage("doesnt-matter")))
                    assert.that(stateMachine.popNextStep() as TaskStep, isA<CreateContainerStep>())
                }

                on("receiving a 'container created' event") {
                    val dockerContainer = DockerContainer("some-container-id", "some-container-name")
                    stateMachine.processEvent(ContainerCreatedEvent(container, dockerContainer))

                    it("gives the next step as running the container for the task container") {
                        assert.that(stateMachine.popNextStep(), equalTo<TaskStep>(RunContainerStep(container, dockerContainer)))
                    }

                    it("does not return any further steps") {
                        assert.that(stateMachine.popNextStep(), absent())
                    }
                }

                on("receiving a 'container creation failed' event") {
                    stateMachine.processEvent(ContainerCreationFailedEvent(container, "Something went wrong"))

                    it("gives the next step as displaying an error to the user") {
                        assert.that(stateMachine.popNextStep(), equalTo<TaskStep>(DisplayTaskFailureStep("Could not create Docker container for container 'some-container': Something went wrong")))
                    }

                    it("does not return any further steps") {
                        assert.that(stateMachine.popNextStep(), absent())
                    }
                }
            }

            describe("after processing the 'run container' step") {
                val dockerContainer = DockerContainer("some-container-id", "some-container-name")

                beforeEachTest {
                    assert.that(stateMachine.popNextStep(), equalTo<TaskStep>(BeginTaskStep))
                    stateMachine.processEvent(TaskStartedEvent)
                    assert.that(stateMachine.popNextStep() as TaskStep, isA<BuildImageStep>())
                    stateMachine.processEvent(ImageBuiltEvent(container, DockerImage("doesnt-matter")))
                    assert.that(stateMachine.popNextStep() as TaskStep, isA<CreateContainerStep>())
                    stateMachine.processEvent(ContainerCreatedEvent(container, dockerContainer))
                    assert.that(stateMachine.popNextStep() as TaskStep, isA<RunContainerStep>())
                }

                on("receiving a 'container exited' event") {
                    stateMachine.processEvent(ContainerExitedEvent(container, 123))

                    it("gives the next step as removing the container for the task container") {
                        assert.that(stateMachine.popNextStep(), equalTo<TaskStep>(RemoveContainerStep(container, dockerContainer)))
                    }

                    it("does not return any further steps") {
                        assert.that(stateMachine.popNextStep(), absent())
                    }
                }

                on("receiving a 'container run failed' event") {
                    stateMachine.processEvent(ContainerRunFailedEvent(container, "Something went wrong"))

                    it("gives the next step as displaying an error to the user") {
                        assert.that(stateMachine.popNextStep(), equalTo<TaskStep>(DisplayTaskFailureStep("""
                            |Could not run container 'some-container': Something went wrong
                            |
                            |This container has not been removed, so you may need to clean up this container yourself by running 'docker rm --force some-container-id'.
                            """.trimMargin())))
                    }

                    it("does not return any further steps") {
                        assert.that(stateMachine.popNextStep(), absent())
                    }
                }
            }

            describe("after processing the 'remove container' step") {
                val exitCode = 123

                beforeEachTest {
                    assert.that(stateMachine.popNextStep(), equalTo<TaskStep>(BeginTaskStep))
                    stateMachine.processEvent(TaskStartedEvent)
                    assert.that(stateMachine.popNextStep() as TaskStep, isA<BuildImageStep>())
                    stateMachine.processEvent(ImageBuiltEvent(container, DockerImage("doesnt-matter")))
                    assert.that(stateMachine.popNextStep() as TaskStep, isA<CreateContainerStep>())
                    stateMachine.processEvent(ContainerCreatedEvent(container, DockerContainer("some-id", "some-name")))
                    assert.that(stateMachine.popNextStep() as TaskStep, isA<RunContainerStep>())
                    stateMachine.processEvent(ContainerExitedEvent(container, exitCode))
                    assert.that(stateMachine.popNextStep() as TaskStep, isA<RemoveContainerStep>())
                }

                on("receiving a 'container removed' event") {
                    stateMachine.processEvent(ContainerRemovedEvent(container))

                    it("gives the next step as finishing the task") {
                        assert.that(stateMachine.popNextStep(), equalTo<TaskStep>(FinishTaskStep(exitCode)))
                    }

                    it("does not return any further steps") {
                        assert.that(stateMachine.popNextStep(), absent())
                    }
                }

                on("receiving a 'container removal failed' event") {
                    stateMachine.processEvent(ContainerRemovalFailedEvent(container, "Something went wrong"))

                    it("gives the next step as displaying an error to the user") {
                        assert.that(stateMachine.popNextStep(), equalTo<TaskStep>(DisplayTaskFailureStep("""
                            |After the task completed with exit code 123, the container 'some-container' could not be removed: Something went wrong
                            |
                            |This container may not have been removed, so you may need to clean up this container yourself by running 'docker rm --force some-id'.
                            """.trimMargin())))
                    }

                    it("does not return any further steps") {
                        assert.that(stateMachine.popNextStep(), absent())
                    }
                }
            }
        }
    }
})

data class CreateForEachTest<T>(val spec: SpecBody, val creator: () -> T) : ReadOnlyProperty<Any?, T> {
    private var value: T? = null

    init {
        spec.beforeEachTest {
            value = creator()
        }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (value == null) {
            throw IllegalStateException("Value has not been initialised")
        }

        return value!!
    }
}
