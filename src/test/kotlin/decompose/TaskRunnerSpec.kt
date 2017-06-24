package decompose

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import decompose.config.*
import decompose.docker.*
import decompose.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TaskRunnerSpec : Spek({
    describe("a task runner") {
        on("running a task with no dependencies specified, and no implicit dependencies specified for the container") {
            val container = Container("the_container", "/build_dir")
            val command = "do-things.sh"
            val task = Task("the_task", TaskRunConfiguration("the_container", command))
            val config = Configuration(
                    "the_project",
                    TaskMap(task),
                    ContainerMap(container)
            )

            val network = DockerNetwork("the-network")
            val builtImage = DockerImage("the_image_id")
            val dockerContainer = DockerContainer("the_container_id", container.name)
            val expectedExitCode = 201

            val dockerClient = mock<DockerClient> {
                on { createNewBridgeNetwork() } doReturn network
                on { build(config.projectName, container) } doReturn builtImage
                on { create(container, command, builtImage, network) } doReturn dockerContainer
                on { run(dockerContainer) } doReturn DockerContainerRunResult(expectedExitCode)
            }

            val eventLogger = mock<EventLogger>()
            val dependencyManager = mock<DependencyRuntimeManager>()
            val dependencyManagerFactory = mock<DependencyRuntimeManagerFactory> {
                on { create(config, task) } doReturn dependencyManager
            }

            val actualExitCode = TaskRunner(dockerClient, eventLogger, dependencyManagerFactory).run(config, "the_task")

            it("logs that it is building the image before it builds it") {
                inOrder(eventLogger, dockerClient) {
                    verify(eventLogger).imageBuildStarting(container)
                    verify(dockerClient).build(config.projectName, container)
                }
            }

            it("logs that it is running the command before it runs it") {
                inOrder(eventLogger, dockerClient) {
                    verify(eventLogger).commandStarting(container, command)
                    verify(dockerClient).run(dockerContainer)
                }
            }

            it("returns the exit code of the container") {
                assert.that(actualExitCode, equalTo(expectedExitCode))
            }
        }

        on("running a task that doesn't exist") {
            val config = Configuration(
                    "the_project",
                    TaskMap(),
                    ContainerMap()
            )

            val runner = TaskRunner(mock<DockerClient>(), mock<EventLogger>(), mock<DependencyRuntimeManagerFactory>())

            it("fails with an appropriate error message") {
                assert.that({ runner.run(config, "the_task_that_doesnt_exist") }, throws<ExecutionException>(withMessage("The task 'the_task_that_doesnt_exist' does not exist.")))
            }
        }

        on("running a task that runs a container that doesn't exist") {
            val config = Configuration(
                    "the_project",
                    TaskMap(Task("the_task", TaskRunConfiguration("the_container", "do-things.sh"))),
                    ContainerMap()
            )

            val runner = TaskRunner(mock<DockerClient>(), mock<EventLogger>(), mock<DependencyRuntimeManagerFactory>())

            it("fails with an appropriate error message") {
                assert.that({ runner.run(config, "the_task") }, throws<ExecutionException>(withMessage("The container 'the_container' referenced by task 'the_task' does not exist.")))
            }
        }

        on("running a task that has a dependency") {
            val taskContainer = Container("the_container", "/build_dir")
            val dependencyContainer = Container("the_dependency", "/other_build_dir")
            val command = "do-things.sh"
            val task = Task("the_task", TaskRunConfiguration("the_container", command), dependencies = setOf(dependencyContainer.name))

            val config = Configuration(
                    "the_project",
                    TaskMap(task),
                    ContainerMap(taskContainer, dependencyContainer)
            )

            val builtImage = DockerImage("the_image_id")
            val network = DockerNetwork("the-network")
            val dockerContainer = DockerContainer("the_container_id", taskContainer.name)
            val expectedExitCode = 201

            val dockerClient = mock<DockerClient> {
                on { createNewBridgeNetwork() } doReturn network
                on { build(config.projectName, taskContainer) } doReturn builtImage
                on { create(taskContainer, command, builtImage, network) } doReturn dockerContainer
                on { run(dockerContainer) } doReturn DockerContainerRunResult(expectedExitCode)
            }

            val eventLogger = mock<EventLogger>()
            val dependencyManager = mock<DependencyRuntimeManager>()
            val dependencyManagerFactory = mock<DependencyRuntimeManagerFactory> {
                on { create(config, task) } doReturn dependencyManager
            }

            val actualExitCode = TaskRunner(dockerClient, eventLogger, dependencyManagerFactory).run(config, "the_task")

            it("logs that it is building the image before it builds it") {
                inOrder(eventLogger, dockerClient) {
                    verify(eventLogger).imageBuildStarting(taskContainer)
                    verify(dockerClient).build(config.projectName, taskContainer)
                }
            }

            it("logs that it is running the command before it runs it") {
                inOrder(eventLogger, dockerClient) {
                    verify(eventLogger).commandStarting(taskContainer, command)
                    verify(dockerClient).run(dockerContainer)
                }
            }

            it("returns the exit code of the container") {
                assert.that(actualExitCode, equalTo(expectedExitCode))
            }

            it("performs dependency operations in the correct order") {
                inOrder(dockerClient, dependencyManager) {
                    verify(dependencyManager).buildImages()
                    verify(dependencyManager).startDependencies(network)
                    verify(dockerClient).run(dockerContainer)
                    verify(dependencyManager).stopDependencies()
                    verify(dockerClient).deleteNetwork(network)
                }
            }
        }
    }
})
