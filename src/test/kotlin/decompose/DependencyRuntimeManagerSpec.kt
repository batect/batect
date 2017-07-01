package decompose

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import decompose.config.Container
import decompose.docker.DockerClient
import decompose.docker.DockerContainer
import decompose.docker.DockerImage
import decompose.docker.DockerNetwork
import decompose.docker.HealthStatus
import decompose.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object DependencyRuntimeManagerSpec : Spek({
    describe("a dependency runtime manager") {
        val projectName = "the-project"

        on("building images for dependencies") {
            val dependency = Container("dependency1", "/build_dir")
            val dependencies = setOf(dependency)
            val eventLogger = mock<EventLogger>()
            val dockerClient = mock<DockerClient>()

            val dependencyManager = DependencyRuntimeManager(projectName, dependencies, eventLogger, dockerClient)
            dependencyManager.buildImages()

            it("logs that it is building the image and then builds it") {
                inOrder(eventLogger, dockerClient) {
                    verify(eventLogger).imageBuildStarting(dependency)
                    verify(dockerClient).build(projectName, dependency)
                }
            }
        }

        describe("starting dependencies") {
            mapOf(
                    "starting a dependency with no health check" to HealthStatus.NoHealthCheck,
                    "starting a dependency with a health check that succeeds" to HealthStatus.BecameHealthy
            ).forEach { description, healthStatus ->
                on(description) {
                    val dependency = Container("dependency1", "/build_dir")
                    val dependencies = setOf(dependency)
                    val eventLogger = mock<EventLogger>()
                    val network = DockerNetwork("the-network")

                    val builtImage = DockerImage("built-image")
                    val createdContainer = DockerContainer("container-for-dependency", dependency.name)
                    val dockerClient = mock<DockerClient> {
                        on { build(projectName, dependency) } doReturn builtImage
                        on { create(dependency, null, builtImage, network) } doReturn createdContainer
                        on { waitForHealthStatus(createdContainer) } doReturn healthStatus
                    }

                    val dependencyManager = DependencyRuntimeManager(projectName, dependencies, eventLogger, dockerClient)
                    dependencyManager.buildImages()
                    dependencyManager.startDependencies(network)

                    it("logs that it is starting the container and then runs it") {
                        inOrder(eventLogger, dockerClient) {
                            verify(eventLogger).dependencyStarting(dependency)
                            verify(dockerClient).start(createdContainer)
                        }
                    }

                    it("waits for the container to start") {
                        verify(dockerClient).waitForHealthStatus(createdContainer)
                    }
                }
            }

            listOf(
                    DependencyStartFailureCase(
                            "starting a dependency that exits before its healthcheck reports that it is healthy",
                            HealthStatus.Exited,
                            "Dependency 'dependency1' exited unexpectedly."
                    ),
                    DependencyStartFailureCase(
                            "starting a dependency that reports that it is unhealthy",
                            HealthStatus.BecameUnhealthy,
                            "Dependency 'dependency1' started but reported that it is not healthy."
                    )
            ).forEach { (description, healthStatus, expectedExceptionMessage) ->
                on(description) {
                    val dependency = Container("dependency1", "/build_dir")
                    val dependencies = setOf(dependency)
                    val eventLogger = mock<EventLogger>()
                    val network = DockerNetwork("the-network")

                    val builtImage = DockerImage("built-image")
                    val createdContainer = DockerContainer("container-for-dependency", dependency.name)
                    val dockerClient = mock<DockerClient> {
                        on { build(projectName, dependency) } doReturn builtImage
                        on { create(dependency, null, builtImage, network) } doReturn createdContainer
                        on { waitForHealthStatus(createdContainer) } doReturn healthStatus
                    }

                    val dependencyManager = DependencyRuntimeManager(projectName, dependencies, eventLogger, dockerClient)
                    dependencyManager.buildImages()

                    it("throws an appropriate exception") {
                        assert.that({ dependencyManager.startDependencies(network) }, throws<DependencyStartException>(withMessage(expectedExceptionMessage)))
                    }
                }
            }

            on("starting dependencies without first building images") {
                val eventLogger = mock<EventLogger>()
                val dockerClient = mock<DockerClient>()
                val dependencyManager = DependencyRuntimeManager(projectName, emptySet(), eventLogger, dockerClient)
                val network = DockerNetwork("the-network")

                it("raises an appropriate exception") {
                    assert.that({ dependencyManager.startDependencies(network) }, throws<ExecutionException>(withMessage("Cannot start dependencies if their images have not yet been built. Call buildImages() before calling startDependencies().")))
                }
            }
        }

        describe("stopping dependencies") {
            on("stopping dependencies after starting them") {
                val dependency1 = Container("dependency1", "/build_dir")
                val dependency2 = Container("dependency2", "/build_dir")
                val dependencies = setOf(dependency1, dependency2)
                val eventLogger = mock<EventLogger>()
                val network = DockerNetwork("the-network")

                val builtImage1 = DockerImage("built-image-1")
                val builtImage2 = DockerImage("built-image-2")
                val createdContainer1 = DockerContainer("container-for-dependency-1", dependency1.name)
                val createdContainer2 = DockerContainer("container-for-dependency-2", dependency2.name)

                val dockerClient = mock<DockerClient> {
                    on { build(projectName, dependency1) } doReturn builtImage1
                    on { build(projectName, dependency2) } doReturn builtImage2
                    on { create(dependency1, null, builtImage1, network) } doReturn createdContainer1
                    on { create(dependency2, null, builtImage2, network) } doReturn createdContainer2
                    on { waitForHealthStatus(any()) } doReturn HealthStatus.BecameHealthy
                }

                val dependencyManager = DependencyRuntimeManager(projectName, dependencies, eventLogger, dockerClient)
                dependencyManager.buildImages()
                dependencyManager.startDependencies(network)
                dependencyManager.stopDependencies()

                it("stops the created container") {
                    verify(dockerClient).stop(createdContainer1)
                    verify(dockerClient).stop(createdContainer2)
                }
            }
        }
    }
})

private data class DependencyStartFailureCase(val description: String, val healthStatus: HealthStatus, val expectedExceptionMessage: String)
