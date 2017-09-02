package batect.model.events

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import batect.model.steps.RunContainerStep
import batect.model.steps.StartContainerStep
import batect.config.Container
import batect.docker.DockerContainer
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ContainerBecameHealthyEventSpec : Spek({
    describe("a 'container became healthy' event") {
        val containerA = Container("container-a", "/container-a-build-dir")
        val event = ContainerBecameHealthyEvent(containerA)

        describe("being applied") {
            describe("when the container that became healthy (A) is depended on by another container (B)") {
                val otherDependencyOfB = Container("other-dependency", "/other-dependency-build-dir")
                val containerB = Container("container-b", "/container-b-build-dir", dependencies = setOf(containerA.name, otherDependencyOfB.name))

                val context = mock<TaskEventContext>()

                beforeEachTest {
                    reset(context)

                    whenever(context.containersThatDependOn(containerA)).doReturn(setOf(containerB))
                    whenever(context.dependenciesOf(containerB)).doReturn(setOf(containerA, otherDependencyOfB))
                }

                describe("and B's Docker container has been created") {
                    val containerBDockerContainer = DockerContainer("container-b-container", "container-b")

                    beforeEachTest {
                        whenever(context.getPastEventsOfType<ContainerCreatedEvent>()).doReturn(setOf(
                                ContainerCreatedEvent(containerB, containerBDockerContainer)
                        ))
                    }

                    describe("and all other dependencies of container B have become healthy") {
                        beforeEachTest {
                            whenever(context.getPastEventsOfType<ContainerBecameHealthyEvent>()).doReturn(setOf(
                                    event,
                                    ContainerBecameHealthyEvent(otherDependencyOfB)
                            ))
                        }

                        on("and container B is the task container") {
                            whenever(context.isTaskContainer(containerB)).doReturn(true)

                            event.apply(context)

                            it("queues a 'run container' step for container B") {
                                verify(context).queueStep(RunContainerStep(containerB, containerBDockerContainer))
                            }
                        }

                        on("and container B is a dependency container") {
                            whenever(context.isTaskContainer(containerB)).doReturn(false)

                            event.apply(context)

                            it("queues a 'start container' step for container B") {
                                verify(context).queueStep(StartContainerStep(containerB, containerBDockerContainer))
                            }
                        }
                    }

                    on("and not all other dependencies of container B have become healthy") {
                        whenever(context.getPastEventsOfType<ContainerBecameHealthyEvent>()).doReturn(setOf(event))

                        event.apply(context)

                        it("does not queue any further work") {
                            verify(context, never()).queueStep(any())
                        }
                    }
                }

                describe("and container B's Docker container has not been created yet") {
                    beforeEachTest {
                        whenever(context.getPastEventsOfType<ContainerCreatedEvent>()).doReturn(emptySet())
                    }

                    on("and all dependencies of B have become healthy") {
                        whenever(context.getPastEventsOfType<ContainerBecameHealthyEvent>()).doReturn(setOf(
                                event,
                                ContainerBecameHealthyEvent(otherDependencyOfB)
                        ))

                        event.apply(context)

                        it("does not queue any further work") {
                            verify(context, never()).queueStep(any())
                        }
                    }

                    on("and all dependencies of B have not become healthy") {
                        whenever(context.getPastEventsOfType<ContainerBecameHealthyEvent>()).doReturn(setOf(event))

                        event.apply(context)

                        it("does not queue any further work") {
                            verify(context, never()).queueStep(any())
                        }
                    }
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
                assertThat(event.toString(), equalTo("ContainerBecameHealthyEvent(container: 'container-a')"))
            }
        }
    }
})
