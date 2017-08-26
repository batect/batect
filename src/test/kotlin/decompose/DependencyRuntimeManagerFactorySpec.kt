package decompose

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import decompose.config.Configuration
import decompose.config.Container
import decompose.config.ContainerMap
import decompose.config.Task
import decompose.config.TaskMap
import decompose.config.TaskRunConfiguration
import decompose.docker.DockerClient
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object DependencyRuntimeManagerFactorySpec : Spek({
    describe("a dependency runtime manager factory") {
        val dependencyResolver = mock<DependencyResolver>()
        val eventLogger = mock<EventLogger>()
        val dockerClient = mock<DockerClient>()
        val factory = DependencyRuntimeManagerFactory(dependencyResolver, eventLogger, dockerClient)

        on("creating a dependency runtime manager") {
            val task = Task("the-task", TaskRunConfiguration("some-container", "some-command"))
            val config = Configuration("the-project", TaskMap(task), ContainerMap())

            val dependencies = setOf(Container("the-dependency", "/some-build-dir"))
            whenever(dependencyResolver.resolveDependencies(config, task)).thenReturn(dependencies)

            val manager = factory.create(config, task)

            it("creates a new dependency runtime manager with the dependencies of the given task") {
                assertThat(manager.dependencies, equalTo(dependencies))
            }

            it("creates a new dependency runtime manager with the given project's name") {
                assertThat(manager.projectName, equalTo(config.projectName))
            }

            it("creates a new dependency runtime manager with the given event logger") {
                assertThat(manager.eventLogger, equalTo(eventLogger))
            }

            it("creates a new dependency runtime manager with the given Docker client") {
                assertThat(manager.dockerClient, equalTo(dockerClient))
            }
        }
    }
})
