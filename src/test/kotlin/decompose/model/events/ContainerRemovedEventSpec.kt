package decompose.model.events

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import decompose.CreateContainerStep
import decompose.DeleteTaskNetworkStep
import decompose.config.Container
import decompose.docker.DockerImage
import decompose.docker.DockerNetwork
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ContainerRemovedEventSpec : Spek({
    describe("a 'container removed' event") {
        val container = Container("container-1", "/build-dir")
        val event = ContainerRemovedEvent(container)

        describe("being applied") {
            val otherContainer1 = Container("other-container-1", "/other-container-1-build-dir")
            val otherContainer2 = Container("other-container-2", "/other-container-2-build-dir")
            val containerThatFailedToCreate = Container("failed-to-create", "/failed-to-create-build-dir")
            val image = DockerImage("some-image")
            val network = DockerNetwork("the-network")

            on("when all containers with a pending or processed creation step have been removed or reported that they failed to be created") {
                val context = mock<TaskEventContext> {
                    on { getPendingAndProcessedStepsOfType<CreateContainerStep>() } doReturn setOf(
                            CreateContainerStep(container, image, network),
                            CreateContainerStep(otherContainer1, image, network),
                            CreateContainerStep(otherContainer2, image, network),
                            CreateContainerStep(containerThatFailedToCreate, image, network)
                    )

                    on { getPastEventsOfType<ContainerRemovedEvent>() } doReturn setOf(
                            event,
                            ContainerRemovedEvent(otherContainer1),
                            ContainerRemovedEvent(otherContainer2)
                    )

                    on { getPastEventsOfType<ContainerCreationFailedEvent>() } doReturn setOf(
                            ContainerCreationFailedEvent(containerThatFailedToCreate, "Some message")
                    )

                    on { getSinglePastEventOfType<TaskNetworkCreatedEvent>() } doReturn TaskNetworkCreatedEvent(network)
                }

                event.apply(context)

                it("queues a 'delete task network' step") {
                    verify(context).queueStep(DeleteTaskNetworkStep(network))
                }
            }

            on("when some containers with a pending or processed creation step have not been removed or reported that they failed to be created") {
                val context = mock<TaskEventContext> {
                    on { getPendingAndProcessedStepsOfType<CreateContainerStep>() } doReturn setOf(
                            CreateContainerStep(container, image, network),
                            CreateContainerStep(otherContainer1, image, network),
                            CreateContainerStep(otherContainer2, image, network),
                            CreateContainerStep(containerThatFailedToCreate, image, network)
                    )

                    on { getPastEventsOfType<ContainerRemovedEvent>() } doReturn setOf(
                            event,
                            ContainerRemovedEvent(otherContainer1)
                    )

                    on { getPastEventsOfType<ContainerCreationFailedEvent>() } doReturn setOf(
                            ContainerCreationFailedEvent(containerThatFailedToCreate, "Some message")
                    )
                }

                event.apply(context)

                it("does not queue any further work") {
                    verify(context, never()).queueStep(any())
                }
            }
        }

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assertThat(event.toString(), equalTo("ContainerRemovedEvent(container: 'container-1')"))
            }
        }
    }
})
