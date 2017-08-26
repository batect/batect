package decompose.model.events

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import decompose.CleanUpContainerStep
import decompose.RunContainerStep
import decompose.StartContainerStep
import decompose.config.Container
import decompose.docker.DockerContainer
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ContainerCreatedEventSpec : Spek({
    describe("a 'container created' event") {
        val dependency1 = Container("dependency-container-1", "/dependency-container-1-build-dir")
        val dependency2 = Container("dependency-container-2", "/dependency-container-2-build-dir")

        val container = Container("container-1", "/container-1-build-dir", dependencies = setOf(dependency1.name, dependency2.name))
        val dockerContainer = DockerContainer("docker-container-1", "container-1")
        val event = ContainerCreatedEvent(container, dockerContainer)

        describe("being applied") {
            describe("when all of the container's dependencies are healthy") {
                on("when the container is the task container") {
                    val context = mock<TaskEventContext> {
                        on { isTaskContainer(container) } doReturn true
                        on { dependenciesOf(container) } doReturn setOf(
                                dependency1,
                                dependency2
                        )
                        on { getPastEventsOfType<ContainerBecameHealthyEvent>() } doReturn setOf(
                                ContainerBecameHealthyEvent(dependency1),
                                ContainerBecameHealthyEvent(dependency2)
                        )
                    }

                    event.apply(context)

                    it("queues a 'run container' step") {
                        verify(context).queueStep(RunContainerStep(container, dockerContainer))
                    }
                }

                on("when the container is a dependency container") {
                    val context = mock<TaskEventContext> {
                        on { isTaskContainer(container) } doReturn false
                        on { dependenciesOf(container) } doReturn setOf(
                                dependency1,
                                dependency2
                        )
                        on { getPastEventsOfType<ContainerBecameHealthyEvent>() } doReturn setOf(
                                ContainerBecameHealthyEvent(dependency1),
                                ContainerBecameHealthyEvent(dependency2)
                        )
                    }

                    event.apply(context)

                    it("queues a 'start container' step") {
                        verify(context).queueStep(StartContainerStep(container, dockerContainer))
                    }
                }
            }

            on("when not all of the container's dependencies are healthy yet") {
                val context = mock<TaskEventContext> {
                    on { dependenciesOf(container) } doReturn setOf(
                            dependency1,
                            dependency2
                    )
                    on { getPastEventsOfType<ContainerBecameHealthyEvent>() } doReturn setOf(
                            ContainerBecameHealthyEvent(dependency1)
                    )
                }

                event.apply(context)

                it("does not queue any further work") {
                    verify(context, never()).queueStep(any())
                }
            }

            on("when the task is aborting") {
                val context = mock<TaskEventContext> {
                    on { isAborting } doReturn true
                }

                event.apply(context)

                it("queues a 'clean up container' step") {
                    verify(context).queueStep(CleanUpContainerStep(container, dockerContainer))
                }
            }
        }

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assert.that(event.toString(), equalTo("ContainerCreatedEvent(container: 'container-1', Docker container ID: 'docker-container-1')"))
            }
        }
    }
})
