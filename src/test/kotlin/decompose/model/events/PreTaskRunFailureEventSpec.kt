package decompose.model.events

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import decompose.model.steps.BuildImageStep
import decompose.model.steps.CleanUpContainerStep
import decompose.model.steps.CreateContainerStep
import decompose.model.steps.CreateTaskNetworkStep
import decompose.model.steps.DeleteTaskNetworkStep
import decompose.model.steps.DisplayTaskFailureStep
import decompose.model.steps.StartContainerStep
import decompose.model.steps.WaitForContainerToBecomeHealthyStep
import decompose.config.Container
import decompose.docker.DockerContainer
import decompose.docker.DockerNetwork
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object PreTaskRunFailureEventSpec : Spek({
    describe("a pre-task run failure event") {
        val event = object : PreTaskRunFailureEvent() {
            override val messageToDisplay: String
                get() = "This is the message to display"
        }

        describe("being applied") {
            on("when the task network has not been created yet") {
                val context = mock<TaskEventContext> {
                    on { getPastEventsOfType<ContainerCreatedEvent>() } doReturn emptySet<ContainerCreatedEvent>()
                    on { getSinglePastEventOfType<TaskNetworkCreatedEvent>() } doReturn null as TaskNetworkCreatedEvent?
                }

                event.apply(context)

                it("aborts the task") {
                    verify(context).abort()
                }

                it("queues a step to display an error message to the user") {
                    verify(context).queueStep(DisplayTaskFailureStep("This is the message to display"))
                }

                it("removes all pending image build steps") {
                    verify(context).removePendingStepsOfType(BuildImageStep::class)
                }

                it("removes all pending container creation steps") {
                    verify(context).removePendingStepsOfType(CreateContainerStep::class)
                }

                it("removes all pending network creation steps") {
                    verify(context).removePendingStepsOfType(CreateTaskNetworkStep::class)
                }

                it("removes all pending start container steps") {
                    verify(context).removePendingStepsOfType(StartContainerStep::class)
                }

                it("removes all pending 'wait for container to become healthy' steps") {
                    verify(context).removePendingStepsOfType<WaitForContainerToBecomeHealthyStep>()
                }
            }

            on("when the task network has been created but no containers have been created") {
                val network = DockerNetwork("the-id")
                val context = mock<TaskEventContext> {
                    on { getPastEventsOfType<ContainerCreatedEvent>() } doReturn emptySet<ContainerCreatedEvent>()
                    on { getSinglePastEventOfType<TaskNetworkCreatedEvent>() } doReturn TaskNetworkCreatedEvent(network)
                }

                event.apply(context)

                it("aborts the task") {
                    verify(context).abort()
                }

                it("queues a step to display an error message to the user") {
                    verify(context).queueStep(DisplayTaskFailureStep("This is the message to display"))
                }

                it("removes all pending image build steps") {
                    verify(context).removePendingStepsOfType<BuildImageStep>()
                }

                it("removes all pending container creation steps") {
                    verify(context).removePendingStepsOfType<CreateContainerStep>()
                }

                it("removes all pending network creation steps") {
                    verify(context).removePendingStepsOfType<CreateTaskNetworkStep>()
                }

                it("removes all pending start container steps") {
                    verify(context).removePendingStepsOfType<StartContainerStep>()
                }

                it("removes all pending 'wait for container to become healthy' steps") {
                    verify(context).removePendingStepsOfType<WaitForContainerToBecomeHealthyStep>()
                }

                it("queues a 'delete task network' step") {
                    verify(context).queueStep(DeleteTaskNetworkStep(network))
                }
            }

            on("when the task network has been created and some containers have been created") {
                val network = DockerNetwork("the-id")
                val container1 = Container("container-1", "/container-1-build-dir")
                val container2 = Container("container-2", "/container-2-build-dir")
                val dockerContainer1 = DockerContainer("docker-container-1", "container-1")
                val dockerContainer2 = DockerContainer("docker-container-2", "container-2")

                val context = mock<TaskEventContext> {
                    on { getPastEventsOfType<ContainerCreatedEvent>() } doReturn setOf(
                            ContainerCreatedEvent(container1, dockerContainer1),
                            ContainerCreatedEvent(container2, dockerContainer2)
                    )
                    on { getSinglePastEventOfType<TaskNetworkCreatedEvent>() } doReturn TaskNetworkCreatedEvent(network)
                }

                event.apply(context)

                it("aborts the task") {
                    verify(context).abort()
                }

                it("queues a step to display an error message to the user") {
                    verify(context).queueStep(DisplayTaskFailureStep("This is the message to display"))
                }

                it("removes all pending image build steps") {
                    verify(context).removePendingStepsOfType<BuildImageStep>()
                }

                it("removes all pending container creation steps") {
                    verify(context).removePendingStepsOfType<CreateContainerStep>()
                }

                it("removes all pending network creation steps") {
                    verify(context).removePendingStepsOfType<CreateTaskNetworkStep>()
                }

                it("removes all pending start container steps") {
                    verify(context).removePendingStepsOfType<StartContainerStep>()
                }

                it("removes all pending 'wait for container to become healthy' steps") {
                    verify(context).removePendingStepsOfType<WaitForContainerToBecomeHealthyStep>()
                }

                it("queues a 'clean up container' step for any container that has already been created") {
                    verify(context).queueStep(CleanUpContainerStep(container1, dockerContainer1))
                    verify(context).queueStep(CleanUpContainerStep(container2, dockerContainer2))
                }

                it("does not queue a 'delete task network' step") {
                    verify(context, never()).queueStep(any<DeleteTaskNetworkStep>())
                }
            }
        }
    }
})
