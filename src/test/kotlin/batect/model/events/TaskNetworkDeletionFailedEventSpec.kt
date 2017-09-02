package batect.model.events

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import batect.model.steps.DisplayTaskFailureStep
import batect.config.Container
import batect.docker.DockerNetwork
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TaskNetworkDeletionFailedEventSpec : Spek({
    describe("a 'task network deletion failed' event") {
        val event = TaskNetworkDeletionFailedEvent("Something went wrong")

        describe("being applied") {
            val network = DockerNetwork("the-network")

            on("when the task is not already aborting") {
                val container = Container("some-container", "/some-build-dir")
                val context = mock<TaskEventContext> {
                    on { getSinglePastEventOfType<TaskNetworkCreatedEvent>() } doReturn TaskNetworkCreatedEvent(network)
                    on { getSinglePastEventOfType<RunningContainerExitedEvent>() } doReturn RunningContainerExitedEvent(container, 123)
                }

                event.apply(context)

                it("queues showing a message to the user") {
                    verify(context).queueStep(DisplayTaskFailureStep("""
                        |After the task exited with exit code 123, the network 'the-network' could not be deleted: Something went wrong
                        |
                        |This network may not have been removed, so you may need to clean up this network yourself by running 'docker network rm the-network'.
                        |""".trimMargin()
                    ))
                }

                it("aborts the task") {
                    verify(context).abort()
                }
            }

            on("when the task is already aborting") {
                val context = mock<TaskEventContext> {
                    on { isAborting } doReturn true
                    on { getSinglePastEventOfType<TaskNetworkCreatedEvent>() } doReturn TaskNetworkCreatedEvent(network)
                }

                event.apply(context)

                it("queues showing a message to the user") {
                    verify(context).queueStep(DisplayTaskFailureStep("""
                        |During clean up after the previous failure, the network 'the-network' could not be deleted: Something went wrong
                        |
                        |This network may not have been removed, so you may need to clean up this network yourself by running 'docker network rm the-network'.
                        |""".trimMargin()
                    ))
                }
            }
        }

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assertThat(event.toString(), equalTo("TaskNetworkDeletionFailedEvent(message: 'Something went wrong')"))
            }
        }
    }
})
