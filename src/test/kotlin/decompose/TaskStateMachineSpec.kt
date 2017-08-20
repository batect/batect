package decompose

import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import decompose.config.Configuration
import decompose.config.Container
import decompose.config.ContainerMap
import decompose.config.Task
import decompose.config.TaskMap
import decompose.config.TaskRunConfiguration
import decompose.docker.DockerContainer
import decompose.docker.DockerImage
import decompose.docker.DockerNetwork
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
                    it("gives the only next step as starting the task") {
                        assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(BeginTaskStep)))
                    }
                }
            }

            describe("after the 'begin task' step has been processed") {
                beforeEachTest {
                    stateMachine.popAllNextSteps()
                }

                on("receiving a 'task started' event") {
                    stateMachine.processEvent(TaskStartedEvent)

                    it("gives the next steps as building the image for the task container and creating the task network (in any order)") {
                        assert.that(stateMachine.popAllNextSteps().toSet(), equalTo(setOf<TaskStep>(
                                BuildImageStep(container),
                                CreateTaskNetworkStep
                        )))
                    }
                }
            }

            describe("after the 'build image' step has been processed") {
                beforeEachTest {
                    stateMachine.processEvent(TaskStartedEvent)
                    stateMachine.popAllNextSteps()
                }

                describe("receiving an 'image built' event") {
                    val image = DockerImage("some-image-id")
                    val event = ImageBuiltEvent(container, image)

                    on("when the task network has not been created yet") {
                        stateMachine.processEvent(event)

                        it("does not return any further steps") {
                            assert.that(stateMachine, hasNoFurtherSteps())
                        }
                    }

                    on("when the task network has been created already") {
                        val network = DockerNetwork("the-network")
                        stateMachine.processEvent(TaskNetworkCreatedEvent(network))
                        stateMachine.popAllNextSteps()

                        stateMachine.processEvent(event)

                        it("gives the only next step as creating the container for the task container") {
                            assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(CreateContainerStep(container, image, network))))
                        }
                    }
                }

                on("receiving a 'image build failed' event") {
                    stateMachine.processEvent(ImageBuildFailedEvent(container, "Something went wrong"))

                    it("gives the only next step as displaying an error to the user") {
                        assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(DisplayTaskFailureStep("Could not build image for container 'some-container': Something went wrong"))))
                    }
                }
            }

            describe("after the 'create network' step is processed") {
                beforeEachTest {
                    stateMachine.processEvent(TaskStartedEvent)
                    stateMachine.popAllNextSteps()
                }

                describe("receiving a 'task network created' event") {
                    val network = DockerNetwork("the-network")
                    val event = TaskNetworkCreatedEvent(network)

                    on("when the task container image has not been built") {
                        stateMachine.processEvent(event)

                        it("does not return any further steps") {
                            assert.that(stateMachine, hasNoFurtherSteps())
                        }
                    }

                    on("when the task container image has been built already") {
                        val image = DockerImage("some-image-id")
                        stateMachine.processEvent(ImageBuiltEvent(container, image))
                        stateMachine.popAllNextSteps()

                        stateMachine.processEvent(event)

                        it("gives the only next step as creating the container for the task container") {
                            assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(CreateContainerStep(container, image, network))))
                        }
                    }
                }

                on("receiving a 'task network creation failed' event") {
                    stateMachine.processEvent(TaskNetworkCreationFailedEvent("Something went wrong"))

                    it("gives the only next step as displaying an error to the user") {
                        assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(DisplayTaskFailureStep("Could not create network for task: Something went wrong"))))
                    }
                }
            }

            describe("after the 'create container' step has been processed") {
                beforeEachTest {
                    stateMachine.processEvent(TaskStartedEvent)
                    stateMachine.processEvent(TaskNetworkCreatedEvent(DockerNetwork("the-network")))
                    stateMachine.processEvent(ImageBuiltEvent(container, DockerImage("doesnt-matter")))
                    stateMachine.popAllNextSteps()
                }

                on("receiving a 'container created' event") {
                    val dockerContainer = DockerContainer("some-container-id", "some-container-name")
                    stateMachine.processEvent(ContainerCreatedEvent(container, dockerContainer))

                    it("gives the only next step as running the container for the task container") {
                        assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(RunContainerStep(container, dockerContainer))))
                    }
                }

                on("receiving a 'container creation failed' event") {
                    stateMachine.processEvent(ContainerCreationFailedEvent(container, "Something went wrong"))

                    it("gives the only next step as displaying an error to the user") {
                        assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(DisplayTaskFailureStep("Could not create Docker container for container 'some-container': Something went wrong"))))
                    }
                }
            }

            describe("after the 'run container' step has been processed") {
                val dockerContainer = DockerContainer("some-container-id", "some-container-name")

                beforeEachTest {
                    stateMachine.processEvent(TaskStartedEvent)
                    stateMachine.processEvent(TaskNetworkCreatedEvent(DockerNetwork("the-network")))
                    stateMachine.processEvent(ImageBuiltEvent(container, DockerImage("doesnt-matter")))
                    stateMachine.processEvent(ContainerCreatedEvent(container, dockerContainer))
                    stateMachine.popAllNextSteps()
                }

                on("receiving a 'container exited' event") {
                    stateMachine.processEvent(ContainerExitedEvent(container, 123))

                    it("gives the only next step as removing the container for the task container") {
                        assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(RemoveContainerStep(container, dockerContainer))))
                    }
                }

                on("receiving a 'container run failed' event") {
                    stateMachine.processEvent(ContainerRunFailedEvent(container, "Something went wrong"))

                    it("gives the only next step as displaying an error to the user") {
                        assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(DisplayTaskFailureStep("""
                            |Could not run container 'some-container': Something went wrong
                            |
                            |This container has not been removed, so you need to clean up this container yourself by running 'docker rm --force some-container-id'.
                            |Furthermore, the network 'the-network' has not been removed, so you need to clean up this network yourself by running 'docker network rm the-network'.
                            """.trimMargin()))))
                    }
                }
            }

            describe("after the 'remove container' step has been processed") {
                val exitCode = 123
                val network = DockerNetwork("the-network")

                beforeEachTest {
                    stateMachine.processEvent(TaskStartedEvent)
                    stateMachine.processEvent(TaskNetworkCreatedEvent(network))
                    stateMachine.processEvent(ImageBuiltEvent(container, DockerImage("doesnt-matter")))
                    stateMachine.processEvent(ContainerCreatedEvent(container, DockerContainer("some-id", "some-name")))
                    stateMachine.processEvent(ContainerExitedEvent(container, exitCode))
                    stateMachine.popAllNextSteps()
                }

                on("receiving a 'container removed' event") {
                    stateMachine.processEvent(ContainerRemovedEvent(container))

                    it("gives the only next step as removing the task network") {
                        assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(DeleteTaskNetworkStep(network))))
                    }
                }

                on("receiving a 'container removal failed' event") {
                    stateMachine.processEvent(ContainerRemovalFailedEvent(container, "Something went wrong"))

                    it("gives the only next step as displaying an error to the user") {
                        assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(DisplayTaskFailureStep("""
                            |After the task completed with exit code 123, the container 'some-container' could not be removed: Something went wrong
                            |
                            |This container may not have been removed, so you may need to clean up this container yourself by running 'docker rm --force some-id'.
                            |Furthermore, the network 'the-network' has not been removed, so you need to clean up this network yourself by running 'docker network rm the-network'.
                            """.trimMargin()))))
                    }
                }
            }

            describe("after the 'delete task network' step has been processed") {
                val exitCode = 123
                val dockerContainer = DockerContainer("some-id", "some-name")

                beforeEachTest {
                    stateMachine.processEvent(TaskStartedEvent)
                    stateMachine.processEvent(TaskNetworkCreatedEvent(DockerNetwork("the-network")))
                    stateMachine.processEvent(ImageBuiltEvent(container, DockerImage("doesnt-matter")))
                    stateMachine.processEvent(ContainerCreatedEvent(container, dockerContainer))
                    stateMachine.processEvent(ContainerExitedEvent(container, exitCode))
                    stateMachine.processEvent(ContainerRemovedEvent(container))
                    stateMachine.popAllNextSteps()
                }

                on("receiving a 'task network deleted' event") {
                    stateMachine.processEvent(TaskNetworkDeletedEvent)

                    it("gives the only next step as finishing the task") {
                        assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(FinishTaskStep(exitCode))))
                    }
                }

                on("receiving a 'task network deletion failed' event") {
                    stateMachine.processEvent(TaskNetworkDeletionFailedEvent("Something went wrong"))

                    it("gives the only next step as displaying an error to the user") {
                        assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(DisplayTaskFailureStep("""
                            |After the task completed with exit code 123, the network 'the-network' could not be deleted: Something went wrong
                            |
                            |This network may not have been removed, so you may need to clean up this network yourself by running 'docker network rm the-network'.
                            """.trimMargin()))))
                    }
                }
            }
        }
    }

    given("a task with a chain of dependencies") {
        val indirectDependency = Container("indirect-dependency-container", "/indirect-dependency-build-dir")
        val directDependency = Container("direct-dependency-container", "/direct-dependency-build-dir", dependencies = setOf(indirectDependency.name))
        val taskContainer = Container("task-container", "/build-dir", dependencies = setOf(directDependency.name))
        val runConfig = TaskRunConfiguration(taskContainer.name, "some-command")
        val task = Task("the-task", runConfig)
        val config = Configuration("the-project", TaskMap(task), ContainerMap(taskContainer, directDependency, indirectDependency))
        val graph = DependencyGraph(config, task)
        val stateMachine by CreateForEachTest(this, { TaskStateMachine(graph) })

        describe("initial state") {
            on("getting the next step") {
                val nextStep = stateMachine.popNextStep()

                it("gives the only next step as starting the task") {
                    assert.that(nextStep, equalTo<TaskStep>(BeginTaskStep))
                }
            }
        }

        describe("after the 'begin task' step has been processed") {
            beforeEachTest {
                stateMachine.popAllNextSteps()
            }

            on("receiving a 'task started' event") {
                stateMachine.processEvent(TaskStartedEvent)

                it("gives the next three steps as building the images for the containers and creating the task network (in any order)") {
                    val steps = stateMachine.popAllNextSteps().toSet()
                    assert.that(steps, equalTo(setOf<TaskStep?>(
                            BuildImageStep(taskContainer),
                            BuildImageStep(directDependency),
                            BuildImageStep(indirectDependency),
                            CreateTaskNetworkStep
                    )))
                }
            }
        }

        describe("after the 'build image' steps have been processed") {
            beforeEachTest {
                stateMachine.processEvent(TaskStartedEvent)
                stateMachine.popAllNextSteps()
            }

            mapOf(
                    "task" to taskContainer,
                    "direct dependency" to directDependency,
                    "indirect dependency" to indirectDependency
            ).forEach { name, container ->
                describe("receiving an 'image built' event for the $name container") {
                    val image = DockerImage("some-image-id")
                    val event = ImageBuiltEvent(container, image)

                    on("when the task network has already been created") {
                        val network = DockerNetwork("the-network")
                        stateMachine.processEvent(TaskNetworkCreatedEvent(network))
                        stateMachine.popAllNextSteps()

                        stateMachine.processEvent(event)

                        it("gives the only next step as creating the container for the $name container") {
                            assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(CreateContainerStep(container, image, network))))
                        }
                    }

                    on("when the task network has not been created yet") {
                        stateMachine.processEvent(event)

                        it("does not return any further steps") {
                            assert.that(stateMachine, hasNoFurtherSteps())
                        }
                    }
                }
            }
        }

        describe("after the 'create network' step is processed") {
            beforeEachTest {
                stateMachine.processEvent(TaskStartedEvent)
            }

            describe("receiving a 'task network created' event") {
                val network = DockerNetwork("the-network")
                val event = TaskNetworkCreatedEvent(network)

                beforeEachTest {
                    stateMachine.popAllNextSteps()
                }

                on("when none of the container images have been built") {
                    stateMachine.processEvent(event)

                    it("does not return any further steps") {
                        assert.that(stateMachine, hasNoFurtherSteps())
                    }
                }

                on("when some of the container images have been built") {
                    val directDependencyImage = DockerImage("direct-dependency-image")
                    val indirectDependencyImage = DockerImage("indirect-dependency-image")
                    stateMachine.processEvent(ImageBuiltEvent(directDependency, directDependencyImage))
                    stateMachine.processEvent(ImageBuiltEvent(indirectDependency, indirectDependencyImage))
                    stateMachine.popAllNextSteps()

                    stateMachine.processEvent(event)

                    it("gives the next steps as creating the containers for the containers with built images (in any order)") {
                        assert.that(stateMachine.popAllNextSteps().toSet(), equalTo(setOf<TaskStep>(
                                CreateContainerStep(directDependency, directDependencyImage, network),
                                CreateContainerStep(indirectDependency, indirectDependencyImage, network)
                        )))
                    }
                }
            }

            describe("receiving a 'task network creation failed' event") {
                val event = TaskNetworkCreationFailedEvent("Something went wrong")

                on("and all 'build image' steps have been popped from the queue already") {
                    stateMachine.popAllNextSteps()
                    stateMachine.processEvent(event)

                    it("gives the only next step as displaying an error to the user") {
                        assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(DisplayTaskFailureStep("Could not create network for task: Something went wrong"))))
                    }
                }

                on("and not all 'build image' steps have been popped from the queue") {
                    stateMachine.processEvent(event)

                    it("gives the only next step as displaying an error to the user") {
                        assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(DisplayTaskFailureStep("Could not create network for task: Something went wrong"))))
                    }
                }
            }
        }

        describe("after the 'create container' steps have been processed") {
            val taskImage = DockerImage("task-image")
            val directDependencyImage = DockerImage("direct-dependency-image")
            val indirectDependencyImage = DockerImage("indirect-dependency-image")

            beforeEachTest {
                stateMachine.processEvent(TaskStartedEvent)
                stateMachine.processEvent(TaskNetworkCreatedEvent(DockerNetwork("the-network")))
                stateMachine.processEvent(ImageBuiltEvent(taskContainer, taskImage))
                stateMachine.processEvent(ImageBuiltEvent(directDependency, directDependencyImage))
                stateMachine.processEvent(ImageBuiltEvent(indirectDependency, indirectDependencyImage))
                stateMachine.popAllNextSteps()
            }

            on("receiving a 'container created' event for the indirect dependency container") {
                val dockerContainer = DockerContainer("the-indirect-dependency-container", "the-indirect-dependency-container")
                stateMachine.processEvent(ContainerCreatedEvent(indirectDependency, dockerContainer))

                it("gives the only next step as starting the container for the indirect dependency") {
                    assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(StartContainerStep(indirectDependency, dockerContainer))))
                }
            }

            describe("receiving a 'container created' event for the direct dependency container") {
                on("when the indirect dependency container has not become healthy yet") {
                    val dockerContainer = DockerContainer("the-direct-dependency-container", "the-direct-dependency-container")
                    stateMachine.processEvent(ContainerCreatedEvent(directDependency, dockerContainer))

                    it("does not return any further steps") {
                        assert.that(stateMachine, hasNoFurtherSteps())
                    }
                }

                on("when the indirect dependency container has already become healthy") {
                    stateMachine.processEvent(ContainerCreatedEvent(indirectDependency, DockerContainer("dont-care", "dont-care")))
                    stateMachine.processEvent(ContainerStartedEvent(indirectDependency))
                    stateMachine.processEvent(ContainerBecameHealthyEvent(indirectDependency))
                    stateMachine.popAllNextSteps()

                    val dockerContainer = DockerContainer("the-direct-dependency-container", "the-direct-dependency-container")
                    stateMachine.processEvent(ContainerCreatedEvent(directDependency, dockerContainer))

                    it("gives the only next step as starting the container for the direct dependency") {
                        assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(StartContainerStep(directDependency, dockerContainer))))
                    }
                }
            }

            describe("receiving a 'container created' event for the task container") {
                on("when the direct dependency container has not become healthy yet") {
                    val dockerContainer = DockerContainer("the-task-container", "the-task-container")
                    stateMachine.processEvent(ContainerCreatedEvent(taskContainer, dockerContainer))

                    it("does not return any further steps") {
                        assert.that(stateMachine, hasNoFurtherSteps())
                    }
                }

                on("when the direct dependency container has become healthy") {
                    stateMachine.processEvent(ContainerCreatedEvent(directDependency, DockerContainer("dont-care", "dont-care")))
                    stateMachine.processEvent(ContainerStartedEvent(directDependency))
                    stateMachine.processEvent(ContainerBecameHealthyEvent(directDependency))
                    stateMachine.popAllNextSteps()

                    val dockerContainer = DockerContainer("the-task-container", "the-task-container")
                    stateMachine.processEvent(ContainerCreatedEvent(taskContainer, dockerContainer))

                    it("gives the only next step as running the task container") {
                        assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(RunContainerStep(taskContainer, dockerContainer))))
                    }
                }
            }
        }

        describe("after the 'start container' steps have been processed") {
            val directDependencyDockerContainer = DockerContainer("direct-dependency", "direct-dependency")
            val indirectDependencyDockerContainer = DockerContainer("indirect-dependency", "indirect-dependency")

            beforeEachTest {
                stateMachine.processEvent(TaskStartedEvent)
                stateMachine.processEvent(ImageBuiltEvent(directDependency, DockerImage("dont-care")))
                stateMachine.processEvent(ImageBuiltEvent(indirectDependency, DockerImage("dont-care")))
                stateMachine.processEvent(ContainerCreatedEvent(directDependency, directDependencyDockerContainer))
                stateMachine.processEvent(ContainerCreatedEvent(indirectDependency, indirectDependencyDockerContainer))
                stateMachine.popAllNextSteps()
            }

            on("receiving a 'container started' event for the direct dependency container") {
                stateMachine.processEvent(ContainerStartedEvent(directDependency))

                it("gives the only next step as waiting for the direct dependency container to become healthy") {
                    assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(WaitForContainerToBecomeHealthyStep(directDependency, directDependencyDockerContainer))))
                }
            }

            on("receiving a 'container started' event for the indirect dependency container") {
                stateMachine.processEvent(ContainerStartedEvent(indirectDependency))

                it("gives the only next step as waiting for the indirect dependency container to become healthy") {
                    assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(WaitForContainerToBecomeHealthyStep(indirectDependency, indirectDependencyDockerContainer))))
                }
            }
        }

        describe("after the 'wait for container to become healthy' steps have been processed") {
            val directDependencyDockerContainer = DockerContainer("direct-dependency", "direct-dependency")
            val indirectDependencyDockerContainer = DockerContainer("indirect-dependency", "indirect-dependency")

            beforeEachTest {
                stateMachine.processEvent(TaskStartedEvent)
                stateMachine.processEvent(TaskNetworkCreatedEvent(DockerNetwork("the-network")))
                stateMachine.processEvent(ImageBuiltEvent(directDependency, DockerImage("dont-care")))
                stateMachine.processEvent(ImageBuiltEvent(indirectDependency, DockerImage("dont-care")))
                stateMachine.processEvent(ContainerCreatedEvent(indirectDependency, indirectDependencyDockerContainer))
                stateMachine.processEvent(ContainerStartedEvent(indirectDependency))
                stateMachine.popAllNextSteps()
            }

            describe("receiving a 'container became healthy' event for the indirect dependency container") {
                on("when the direct dependency container has already been created") {
                    stateMachine.processEvent(ContainerCreatedEvent(directDependency, directDependencyDockerContainer))
                    stateMachine.popAllNextSteps()

                    stateMachine.processEvent(ContainerBecameHealthyEvent(indirectDependency))

                    it("gives the only next step as starting the direct dependency container") {
                        assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(StartContainerStep(directDependency, directDependencyDockerContainer))))
                    }
                }

                on("when the direct dependency container has not been created yet") {
                    stateMachine.processEvent(ContainerBecameHealthyEvent(indirectDependency))

                    it("does not return any further steps") {
                        assert.that(stateMachine, hasNoFurtherSteps())
                    }
                }
            }

            describe("receiving a 'container became healthy' event for the direct dependency container") {
                beforeEachTest {
                    stateMachine.processEvent(ContainerBecameHealthyEvent(indirectDependency))
                    stateMachine.processEvent(ContainerCreatedEvent(directDependency, directDependencyDockerContainer))
                    stateMachine.processEvent(ContainerStartedEvent(directDependency))
                    stateMachine.popAllNextSteps()
                }

                on("when the task container has already been created") {
                    val taskDockerContainer = DockerContainer("task-container", "task-container")
                    stateMachine.processEvent(ContainerCreatedEvent(taskContainer, taskDockerContainer))
                    stateMachine.popAllNextSteps()

                    stateMachine.processEvent(ContainerBecameHealthyEvent(directDependency))

                    it("gives the only next step as running the task container") {
                        assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(RunContainerStep(taskContainer, taskDockerContainer))))
                    }
                }

                on("when the task container has not been created yet") {
                    stateMachine.processEvent(ContainerBecameHealthyEvent(directDependency))

                    it("does not return any further steps") {
                        assert.that(stateMachine, hasNoFurtherSteps())
                    }
                }
            }
        }

        describe("after the 'run container' step has been processed") {
            val taskDockerContainer = DockerContainer("task-container", "task-container")
            val directDependencyDockerContainer = DockerContainer("direct-dependency", "direct-dependency")
            val indirectDependencyDockerContainer = DockerContainer("indirect-dependency", "indirect-dependency")

            beforeEachTest {
                stateMachine.processEvent(TaskStartedEvent)
                stateMachine.processEvent(TaskNetworkCreatedEvent(DockerNetwork("the-network")))
                stateMachine.processEvent(ImageBuiltEvent(taskContainer, DockerImage("doesnt-matter")))
                stateMachine.processEvent(ImageBuiltEvent(directDependency, DockerImage("dont-care")))
                stateMachine.processEvent(ImageBuiltEvent(indirectDependency, DockerImage("dont-care")))
                stateMachine.processEvent(ContainerCreatedEvent(taskContainer, taskDockerContainer))
                stateMachine.processEvent(ContainerCreatedEvent(indirectDependency, indirectDependencyDockerContainer))
                stateMachine.processEvent(ContainerCreatedEvent(directDependency, directDependencyDockerContainer))
                stateMachine.processEvent(ContainerStartedEvent(indirectDependency))
                stateMachine.processEvent(ContainerBecameHealthyEvent(indirectDependency))
                stateMachine.processEvent(ContainerStartedEvent(directDependency))
                stateMachine.processEvent(ContainerBecameHealthyEvent(directDependency))
                stateMachine.popAllNextSteps()
            }

            on("receiving a 'container exited' event") {
                stateMachine.processEvent(ContainerExitedEvent(taskContainer, 123))

                it("gives the next steps as stopping the direct dependency container and removing the task container (in any order)") {
                    assert.that(stateMachine.popAllNextSteps().toSet(), equalTo(setOf<TaskStep>(
                            StopContainerStep(directDependency, directDependencyDockerContainer),
                            RemoveContainerStep(taskContainer, taskDockerContainer)
                    )))
                }
            }
        }

        describe("after the 'stop container' steps have been processed") {
            val taskDockerContainer = DockerContainer("task-container", "task-container")
            val directDependencyDockerContainer = DockerContainer("direct-dependency", "direct-dependency")
            val indirectDependencyDockerContainer = DockerContainer("indirect-dependency", "indirect-dependency")

            beforeEachTest {
                stateMachine.processEvent(TaskStartedEvent)
                stateMachine.processEvent(TaskNetworkCreatedEvent(DockerNetwork("the-network")))
                stateMachine.processEvent(ImageBuiltEvent(taskContainer, DockerImage("doesnt-matter")))
                stateMachine.processEvent(ImageBuiltEvent(directDependency, DockerImage("dont-care")))
                stateMachine.processEvent(ImageBuiltEvent(indirectDependency, DockerImage("dont-care")))
                stateMachine.processEvent(ContainerCreatedEvent(taskContainer, taskDockerContainer))
                stateMachine.processEvent(ContainerCreatedEvent(indirectDependency, indirectDependencyDockerContainer))
                stateMachine.processEvent(ContainerCreatedEvent(directDependency, directDependencyDockerContainer))
                stateMachine.processEvent(ContainerStartedEvent(indirectDependency))
                stateMachine.processEvent(ContainerBecameHealthyEvent(indirectDependency))
                stateMachine.processEvent(ContainerStartedEvent(directDependency))
                stateMachine.processEvent(ContainerBecameHealthyEvent(directDependency))
                stateMachine.processEvent(ContainerExitedEvent(taskContainer, 123))
                stateMachine.popAllNextSteps()
            }

            on("receiving a 'container stopped' event for the direct dependency container") {
                stateMachine.processEvent(ContainerStoppedEvent(directDependency))

                it("gives the next steps as stopping the indirect dependency container and removing the direct dependency container (in any order)") {
                    assert.that(stateMachine.popAllNextSteps().toSet(), equalTo(setOf<TaskStep>(
                            StopContainerStep(indirectDependency, indirectDependencyDockerContainer),
                            RemoveContainerStep(directDependency, directDependencyDockerContainer)
                    )))
                }
            }

            on("receiving a 'container stopped' event for the indirect dependency container") {
                stateMachine.processEvent(ContainerStoppedEvent(directDependency))
                stateMachine.popAllNextSteps()

                stateMachine.processEvent(ContainerStoppedEvent(indirectDependency))

                it("gives the next step as removing the indirect dependency container") {
                    assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(RemoveContainerStep(indirectDependency, indirectDependencyDockerContainer))))
                }
            }
        }

        describe("after running the 'remove container' steps") {
            val network = DockerNetwork("the-network")
            val taskDockerContainer = DockerContainer("task-container", "task-container")
            val directDependencyDockerContainer = DockerContainer("direct-dependency", "direct-dependency")
            val indirectDependencyDockerContainer = DockerContainer("indirect-dependency", "indirect-dependency")

            beforeEachTest {
                stateMachine.processEvent(TaskStartedEvent)
                stateMachine.processEvent(TaskNetworkCreatedEvent(network))
                stateMachine.processEvent(ImageBuiltEvent(taskContainer, DockerImage("doesnt-matter")))
                stateMachine.processEvent(ImageBuiltEvent(directDependency, DockerImage("dont-care")))
                stateMachine.processEvent(ImageBuiltEvent(indirectDependency, DockerImage("dont-care")))
                stateMachine.processEvent(ContainerCreatedEvent(taskContainer, taskDockerContainer))
                stateMachine.processEvent(ContainerCreatedEvent(indirectDependency, indirectDependencyDockerContainer))
                stateMachine.processEvent(ContainerCreatedEvent(directDependency, directDependencyDockerContainer))
                stateMachine.processEvent(ContainerStartedEvent(indirectDependency))
                stateMachine.processEvent(ContainerBecameHealthyEvent(indirectDependency))
                stateMachine.processEvent(ContainerStartedEvent(directDependency))
                stateMachine.processEvent(ContainerBecameHealthyEvent(directDependency))
                stateMachine.processEvent(ContainerExitedEvent(taskContainer, 123))
                stateMachine.processEvent(ContainerStoppedEvent(indirectDependency))
                stateMachine.processEvent(ContainerStoppedEvent(directDependency))
                stateMachine.processEvent(ContainerStoppedEvent(taskContainer))
                stateMachine.popAllNextSteps()
            }

            on("receiving a 'container removed' event for the indirect dependency container") {
                stateMachine.processEvent(ContainerRemovedEvent(indirectDependency))

                it("does not return any further steps") {
                    assert.that(stateMachine, hasNoFurtherSteps())
                }
            }

            on("receiving a 'container removed' event for the direct dependency container") {
                stateMachine.processEvent(ContainerRemovedEvent(directDependency))

                it("does not return any further steps") {
                    assert.that(stateMachine, hasNoFurtherSteps())
                }
            }

            on("receiving a 'container removed' event for the task container") {
                stateMachine.processEvent(ContainerRemovedEvent(taskContainer))

                it("does not return any further steps") {
                    assert.that(stateMachine, hasNoFurtherSteps())
                }
            }

            on("receiving a 'container removed' event for the all containers") {
                stateMachine.processEvent(ContainerRemovedEvent(indirectDependency))
                stateMachine.processEvent(ContainerRemovedEvent(directDependency))
                stateMachine.processEvent(ContainerRemovedEvent(taskContainer))

                it("gives the next step as deleting the task network") {
                    assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(DeleteTaskNetworkStep(network))))
                }
            }
        }

        describe("after running the 'delete network' step") {
            val exitCode = 123
            val network = DockerNetwork("the-network")
            val taskDockerContainer = DockerContainer("task-container", "task-container")
            val directDependencyDockerContainer = DockerContainer("direct-dependency", "direct-dependency")
            val indirectDependencyDockerContainer = DockerContainer("indirect-dependency", "indirect-dependency")

            beforeEachTest {
                stateMachine.processEvent(TaskStartedEvent)
                stateMachine.processEvent(TaskNetworkCreatedEvent(network))
                stateMachine.processEvent(ImageBuiltEvent(taskContainer, DockerImage("doesnt-matter")))
                stateMachine.processEvent(ImageBuiltEvent(directDependency, DockerImage("dont-care")))
                stateMachine.processEvent(ImageBuiltEvent(indirectDependency, DockerImage("dont-care")))
                stateMachine.processEvent(ContainerCreatedEvent(taskContainer, taskDockerContainer))
                stateMachine.processEvent(ContainerCreatedEvent(indirectDependency, indirectDependencyDockerContainer))
                stateMachine.processEvent(ContainerCreatedEvent(directDependency, directDependencyDockerContainer))
                stateMachine.processEvent(ContainerStartedEvent(indirectDependency))
                stateMachine.processEvent(ContainerBecameHealthyEvent(indirectDependency))
                stateMachine.processEvent(ContainerStartedEvent(directDependency))
                stateMachine.processEvent(ContainerBecameHealthyEvent(directDependency))
                stateMachine.processEvent(ContainerExitedEvent(taskContainer, exitCode))
                stateMachine.processEvent(ContainerStoppedEvent(indirectDependency))
                stateMachine.processEvent(ContainerStoppedEvent(directDependency))
                stateMachine.processEvent(ContainerStoppedEvent(taskContainer))
                stateMachine.processEvent(ContainerRemovedEvent(indirectDependency))
                stateMachine.processEvent(ContainerRemovedEvent(directDependency))
                stateMachine.processEvent(ContainerRemovedEvent(taskContainer))
                stateMachine.popAllNextSteps()
            }

            on("receiving a 'task network deleted' event") {
                stateMachine.processEvent(TaskNetworkDeletedEvent)

                it("gives the only next step as finishing the task") {
                    assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(FinishTaskStep(exitCode))))
                }
            }

            on("receiving a 'task network deletion failed' event") {
                stateMachine.processEvent(TaskNetworkDeletionFailedEvent("Something went wrong"))

                it("gives the only next step as displaying an error to the user") {
                    assert.that(stateMachine.popAllNextSteps(), equalTo(listOf<TaskStep>(DisplayTaskFailureStep("""
                            |After the task completed with exit code 123, the network 'the-network' could not be deleted: Something went wrong
                            |
                            |This network may not have been removed, so you may need to clean up this network yourself by running 'docker network rm the-network'.
                            """.trimMargin()))))
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
            throw IllegalStateException("Value '${property.name}' created with CreateForEachTest has not been initialised (are you accessing it outside of a 'beforeEachTest', 'on' or 'it' block?)")
        }

        return value!!
    }
}

private fun TaskStateMachine.popAllNextSteps(): List<TaskStep> {
    val steps = mutableListOf<TaskStep>()

    do {
        val step = this.popNextStep()

        if (step != null) {
            steps.add(step)
        }
    } while (step != null)

    return steps
}

fun hasNoFurtherSteps(): Matcher<TaskStateMachine> =
        object : Matcher<TaskStateMachine> {
            override fun invoke(actual: TaskStateMachine): MatchResult {
                val steps = actual.peekNextSteps()

                if (steps.any()) {
                    return MatchResult.Mismatch("contained steps $steps")
                } else {
                    return MatchResult.Match
                }
            }

            override val description: String get() = "has no further steps"
            override val negatedDescription: String get() = "has further steps"
        }
