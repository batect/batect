package decompose.model.events

import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import decompose.CreateContainerStep
import decompose.config.Container
import decompose.docker.DockerImage
import decompose.docker.DockerNetwork
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ImageBuiltEventSpec : Spek({
    describe("an 'image built' event") {
        val container = Container("container-1", "/container-1-build-dir")
        val image = DockerImage("image-1")
        val event = ImageBuiltEvent(container, image)

        describe("being applied") {
            on("when the task network has not been created yet") {
                val context = mock<TaskEventContext> {
                    on { getSinglePastEventOfType<TaskNetworkCreatedEvent>() } doReturn null as TaskNetworkCreatedEvent?
                }

                event.apply(context)

                it("does not queue any further work") {
                    verify(context, never()).queueStep(any())
                }
            }

            on("when the task network has already been created") {
                val network = DockerNetwork("the-network")
                val context = mock<TaskEventContext> {
                    on { getSinglePastEventOfType<TaskNetworkCreatedEvent>() } doReturn TaskNetworkCreatedEvent(network)
                }

                event.apply(context)

                it("queues a 'create container' step") {
                    verify(context).queueStep(CreateContainerStep(container, image, network))
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
                com.natpryce.hamkrest.assertion.assert.that(event.toString(), equalTo("ImageBuiltEvent(container: 'container-1', image: 'image-1')"))
            }
        }
    }
})
