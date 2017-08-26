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

object TaskNetworkCreatedEventSpec : Spek({
    describe("a 'task network created' event") {
        val network = DockerNetwork("some-network")
        val event = TaskNetworkCreatedEvent(network)

        val container1 = Container("container-1", "/container-1-build-dir")
        val image1 = DockerImage("image-1")

        val container2 = Container("container-2", "/container-2-build-dir")
        val image2 = DockerImage("image-2")

        describe("being applied") {
            on("when no images have been built yet") {
                val context = mock<TaskEventContext> { }
                event.apply(context)

                it("does not queue any further work") {
                    verify(context, never()).queueStep(any())
                }
            }

            on("when some images have been built already") {
                val context = mock<TaskEventContext> {
                    on { getPastEventsOfType<ImageBuiltEvent>() } doReturn setOf(
                            ImageBuiltEvent(container1, image1),
                            ImageBuiltEvent(container2, image2)
                    )
                }

                event.apply(context)

                it("queues 'create container' steps for them") {
                    verify(context).queueStep(CreateContainerStep(container1, image1, network))
                    verify(context).queueStep(CreateContainerStep(container2, image2, network))
                }

                it("does not queue a 'delete task network' step") {
                    verify(context, never()).queueStep(any<DeleteTaskNetworkStep>())
                }
            }

            on("when the task is aborting") {
                val context = mock<TaskEventContext> {
                    on { isAborting } doReturn true
                    on { getPastEventsOfType<ImageBuiltEvent>() } doReturn setOf(
                            ImageBuiltEvent(container1, image1)
                    )
                }

                event.apply(context)

                it("queues a 'delete task network' step") {
                    verify(context).queueStep(DeleteTaskNetworkStep(network))
                }

                it("does not queue any 'create container' steps for the containers with built images") {
                    verify(context, never()).queueStep(any<CreateContainerStep>())
                }
            }
        }

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assertThat(event.toString(), equalTo("TaskNetworkCreatedEvent(network ID: 'some-network')"))
            }
        }
    }
})
