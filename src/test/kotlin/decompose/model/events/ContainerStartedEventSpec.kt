package decompose.model.events

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import decompose.WaitForContainerToBecomeHealthyStep
import decompose.config.Container
import decompose.docker.DockerContainer
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ContainerStartedEventSpec : Spek({
    describe("a 'container started' event") {
        val container = Container("container-1", "/build-dir")
        val event = ContainerStartedEvent(container)

        describe("being applied") {
            on("when the task is not aborting") {
                val dockerContainer = DockerContainer("container-1-dc", "container-1")
                val otherContainer = Container("container-2", "/other-build-dir")
                val otherDockerContainer = DockerContainer("container-2-dc", "container-2")

                val context = mock<TaskEventContext> {
                    on { getPastEventsOfType<ContainerCreatedEvent>() } doReturn setOf(
                            ContainerCreatedEvent(container, dockerContainer),
                            ContainerCreatedEvent(otherContainer, otherDockerContainer)
                    )
                }

                event.apply(context)

                it("queues a 'wait for container to become healthy step'") {
                    verify(context).queueStep(WaitForContainerToBecomeHealthyStep(container, dockerContainer))
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
                assert.that(event.toString(), equalTo("ContainerStartedEvent(container: 'container-1')"))
            }
        }
    }
})
