package decompose

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import decompose.config.*
import decompose.docker.DockerClient
import decompose.docker.DockerContainer
import decompose.docker.DockerImage
import decompose.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TaskRunnerSpec : Spek({
    describe("a task runner") {
        on("running a task with no dependencies specified, and no implicit dependencies specified for the container") {
            val container = Container("the_container", "/build_dir")
            val config = Configuration(
                    "the_project",
                    TaskMap(Task("the_task", TaskRunConfiguration("the_container", "do-things.sh"))),
                    ContainerMap(container)
            )

            val builtImage = DockerImage("the_image_id")
            val dockerContainer = DockerContainer("the_container_id")

            val dockerClient = mock<DockerClient> {
                on { build(container) } doReturn builtImage
                on { create(container, builtImage) } doReturn dockerContainer
            }

            TaskRunner(config, "the_task", dockerClient).run()

            it("builds the image, creates a container and runs the command in the container") {
                verify(dockerClient).run(dockerContainer)
            }
        }

        on("running a task that doesn't exist") {
            val config = Configuration(
                    "the_project",
                    TaskMap(),
                    ContainerMap()
            )

            val runner = TaskRunner(config, "the_task_that_doesnt_exist", mock<DockerClient>())

            it("fails with an appropriate error message") {
                assert.that({ runner.run() }, throws<ExecutionException>(withMessage("The task 'the_task_that_doesnt_exist' does not exist.")))
            }
        }

        on("running a task that runs a container that doesn't exist") {
            val config = Configuration(
                    "the_project",
                    TaskMap(Task("the_task", TaskRunConfiguration("the_container", "do-things.sh"))),
                    ContainerMap()
            )

            val runner = TaskRunner(config, "the_task", mock<DockerClient>())

            it("fails with an appropriate error message") {
                assert.that({ runner.run() }, throws<ExecutionException>(withMessage("The container 'the_container' referenced by task 'the_task' does not exist.")))
            }
        }

        on("running a task that has dependencies") {
            val config = Configuration(
                    "the_project",
                    TaskMap(Task("the_task", TaskRunConfiguration("the_container", "do-things.sh"), setOf("the_dependency"))),
                    ContainerMap(Container("the_container", "/build_dir"))
            )

            val runner = TaskRunner(config, "the_task", mock<DockerClient>())

            it("fails with an appropriate error message") {
                assert.that({ runner.run() }, throws<ExecutionException>(withMessage("Running tasks with dependencies isn't supported yet.")))
            }
        }
    }
})
