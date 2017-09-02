package batect.model.events

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import batect.model.steps.CleanUpContainerStep
import batect.model.steps.DisplayTaskFailureStep
import batect.model.steps.RemoveContainerStep
import batect.model.steps.StopContainerStep
import batect.config.Container
import batect.docker.DockerContainer
import batect.docker.DockerNetwork
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object PostTaskRunCleanupFailureEventSpec : Spek({
    describe("a 'post-task run cleanup failure' event") {
        val container = Container("some-container", "/some-build-dir")
        val event = object : PostTaskRunCleanupFailureEvent(container) {
            override val messageToDisplay: String
                get() = "the container couldn't be somethinged: Something went wrong"
        }

        describe("being applied") {
            val dockerContainer = DockerContainer("some-container-id", "some-container")
            val network = DockerNetwork("some-network")

            on("when the task is already aborting") {
                val context = mock<TaskEventContext>() {
                    on { isAborting } doReturn true
                    on { getSinglePastEventOfType<TaskNetworkCreatedEvent>() } doReturn TaskNetworkCreatedEvent(network)
                    on { getPastEventsOfType<ContainerCreatedEvent>() } doReturn setOf(ContainerCreatedEvent(container, dockerContainer))
                }

                event.apply(context)

                it("queues showing a message to the user") {
                    verify(context).queueStep(DisplayTaskFailureStep("""
                        |During clean up after the previous failure, the container couldn't be somethinged: Something went wrong
                        |
                        |This container may not have been cleaned up completely, so you may need to remove this container yourself by running 'docker rm --force some-container-id'.
                        |Furthermore, the task network cannot be automatically cleaned up, so you will need to clean up this network yourself by running 'docker network rm some-network'.
                        |""".trimMargin()
                    ))
                }
            }

            on("when the task is not already aborting") {
                val otherContainer = Container("some-other-container", "/some-other-build-dir")
                val otherDockerContainer = DockerContainer("some-other-container-id", "some-other-container")

                val context = mock<TaskEventContext>() {
                    on { getSinglePastEventOfType<RunningContainerExitedEvent>() } doReturn RunningContainerExitedEvent(container, 123)
                    on { getSinglePastEventOfType<TaskNetworkCreatedEvent>() } doReturn TaskNetworkCreatedEvent(network)
                    on { getPastEventsOfType<ContainerCreatedEvent>() } doReturn setOf(ContainerCreatedEvent(container, dockerContainer))
                    on { getProcessedStepsOfType<RemoveContainerStep>() } doReturn setOf(RemoveContainerStep(container, dockerContainer))
                    on { getPastEventsOfType<ContainerCreatedEvent>() } doReturn setOf(
                            ContainerCreatedEvent(container, dockerContainer),
                            ContainerCreatedEvent(otherContainer, otherDockerContainer)
                    )
                }

                event.apply(context)

                it("queues showing a message to the user") {
                    verify(context).queueStep(DisplayTaskFailureStep("""
                        |After the task exited with exit code 123, the container couldn't be somethinged: Something went wrong
                        |
                        |This container may not have been cleaned up completely, so you may need to remove this container yourself by running 'docker rm --force some-container-id'.
                        |Furthermore, the task network cannot be automatically cleaned up, so you will need to clean up this network yourself by running 'docker network rm some-network'.
                        |""".trimMargin()
                    ))
                }

                it("aborts the task") {
                    verify(context).abort()
                }

                it("removes all pending stop container steps") {
                    verify(context).removePendingStepsOfType<StopContainerStep>()
                }

                it("removes all pending remove container steps") {
                    verify(context).removePendingStepsOfType<RemoveContainerStep>()
                }

                it("queues 'cleanup container' steps for all containers that have not already been removed") {
                    verify(context).queueStep(CleanUpContainerStep(otherContainer, otherDockerContainer))
                }
            }
        }
    }
})
