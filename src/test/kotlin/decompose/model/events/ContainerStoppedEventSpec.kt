package decompose.model.events

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import decompose.model.steps.RemoveContainerStep
import decompose.model.steps.StopContainerStep
import decompose.config.Container
import decompose.docker.DockerContainer
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ContainerStoppedEventSpec : Spek({
    describe("a 'container stopped' event") {
        val dependency1 = Container("dependency-container-1", "/dependency-container-1-build-dir")
        val dependency2 = Container("dependency-container-2", "/dependency-container-2-build-dir")
        val dependency3 = Container("dependency-container-3", "/dependency-container-3-build-dir", dependencies = setOf(dependency2.name))

        val container = Container("container-1", "/container-1-build-dir", dependencies = setOf(dependency1.name, dependency2.name))
        val event = ContainerStoppedEvent(container)

        describe("being applied") {
            on("when the task is not aborting") {
                val dockerContainer = DockerContainer("container-1-container", "container-1")
                val dependency1DockerContainer = DockerContainer("dependency-container-1-container", "dependency-container-1")
                val dependency2DockerContainer = DockerContainer("dependency-container-2-container", "dependency-container-2")
                val dependency3DockerContainer = DockerContainer("dependency-container-3-container", "dependency-container-3")

                val context = mock<TaskEventContext> {
                    on { getPastEventsOfType<ContainerCreatedEvent>() } doReturn setOf(
                            ContainerCreatedEvent(container, dockerContainer),
                            ContainerCreatedEvent(dependency1, dependency1DockerContainer),
                            ContainerCreatedEvent(dependency2, dependency2DockerContainer),
                            ContainerCreatedEvent(dependency3, dependency3DockerContainer)
                    )
                    on { getPastEventsOfType<ContainerStoppedEvent>() } doReturn setOf(event)
                    on { dependenciesOf(container) } doReturn setOf(dependency1, dependency2)
                    on { containersThatDependOn(dependency1) } doReturn setOf(container)
                    on { containersThatDependOn(dependency2) } doReturn setOf(container, dependency3)
                }

                event.apply(context)

                it("queues a 'remove container' step for the container that stopped") {
                    verify(context).queueStep(RemoveContainerStep(container, dockerContainer))
                }

                it("queues a 'stop container' step for all dependencies of the container that stopped that are not needed by any containers that are still running") {
                    verify(context).queueStep(StopContainerStep(dependency1, dependency1DockerContainer))
                }

                it("does not queue a 'stop container' step for any dependencies of the container that stopped that are still needed by other running containers") {
                    verify(context, never()).queueStep(StopContainerStep(dependency2, dependency2DockerContainer))
                }
            }

            on("when the task is aborting") {
                val context = mock<TaskEventContext> {
                    on { isAborting } doReturn true
                }
                event.apply(context)

                it("does not queue any further work") {
                    verify(context, never()).queueStep(any())
                }
            }
        }

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assertThat(event.toString(), equalTo("ContainerStoppedEvent(container: 'container-1')"))
            }
        }
    }
})
