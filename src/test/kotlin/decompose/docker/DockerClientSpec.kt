package decompose.docker

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.*
import decompose.config.Container
import decompose.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.mockito.ArgumentMatchers.anyString

object DockerClientSpec : Spek({
    describe("a Docker client") {
        val imageLabel = "the-image"
        val labeller = mock<DockerImageLabellingStrategy> {
            on { labelImage(anyString(), any()) } doReturn imageLabel
        }

        val processRunner = mock<ProcessRunner>()
        val creationCommandGenerator = mock<DockerContainerCreationCommandGenerator>()

        val client = DockerClient(labeller, processRunner, creationCommandGenerator)

        beforeEachTest {
            reset(processRunner)
            reset(creationCommandGenerator)
        }

        describe("building an image") {
            given("a container configuration") {
                val container = Container("the-container", "/path/to/build/dir")

                on("a successful build") {
                    val result = client.build("the-project", container)

                    it("builds the image") {
                        verify(processRunner).run(listOf("docker", "build", "--tag", imageLabel, container.buildDirectory))
                    }

                    it("returns the ID of the created image") {
                        assert.that(result.id, equalTo(imageLabel))
                    }
                }

                on("a failed build") {
                    whenever(processRunner.run(any())).thenReturn(1)

                    it("raises an appropriate exception") {
                        assert.that({ client.build("the-project", container) }, throws<ImageBuildFailedException>())
                    }
                }
            }
        }

        describe("creating a container") {
            given("a container configuration and a built image") {
                val container = Container("the-container", "/this/does/not/matter")
                val command = "doStuff"
                val image = DockerImage("the-image")
                val commandLine = listOf("docker", "doStuff", "please")

                beforeEachTest {
                    whenever(creationCommandGenerator.createCommandLine(container, command, image)).thenReturn(commandLine)
                }

                on("a successful creation") {
                    val containerId = "abc123"
                    whenever(processRunner.runAndCaptureOutput(any())).thenReturn(ProcessOutput(0, containerId + "\n"))

                    val result = client.create(container, command, image)

                    it("creates the container") {
                        verify(processRunner).runAndCaptureOutput(commandLine)
                    }

                    it("returns the ID of the created container") {
                        assert.that(result.id, equalTo(containerId))
                    }
                }

                on("a failed creation") {
                    whenever(processRunner.runAndCaptureOutput(any())).thenReturn(ProcessOutput(1, "Something went wrong."))

                    it("raises an appropriate exception") {
                        assert.that({ client.create(container, command, image) }, throws<ContainerCreationFailedException>(withMessage("Creation of container 'the-container' failed. Output from Docker was: Something went wrong.")))
                    }
                }
            }
        }

        describe("running a container") {
            given("a Docker container") {
                val container = DockerContainer("the-container-id")

                on("running the container") {
                    whenever(processRunner.run(any())).thenReturn(123)

                    val result = client.run(container)

                    it("starts the container") {
                        verify(processRunner).run(listOf("docker", "start", "--attach", "--interactive", container.id))
                    }

                    it("returns the exit code from the container") {
                        assert.that(result.exitCode, equalTo(123))
                    }
                }
            }
        }
    }
})

