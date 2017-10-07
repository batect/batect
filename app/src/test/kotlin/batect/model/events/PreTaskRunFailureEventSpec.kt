/*
   Copyright 2017 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.model.events

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import batect.model.steps.BuildImageStep
import batect.model.steps.CleanUpContainerStep
import batect.model.steps.CreateContainerStep
import batect.model.steps.CreateTaskNetworkStep
import batect.model.steps.DeleteTaskNetworkStep
import batect.model.steps.DisplayTaskFailureStep
import batect.model.steps.StartContainerStep
import batect.model.steps.WaitForContainerToBecomeHealthyStep
import batect.config.Container
import batect.docker.DockerContainer
import batect.docker.DockerNetwork
import batect.logging.Logger
import batect.model.BehaviourAfterFailure
import batect.model.steps.PullImageStep
import batect.testutils.InMemoryLogSink
import batect.testutils.imageSourceDoesNotMatter
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.TestContainer
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
            fun TestContainer.itRemovesAllPendingPreTaskSteps(context: TaskEventContext) {
                it("removes all pending image build steps") {
                    verify(context).removePendingStepsOfType(BuildImageStep::class)
                }

                it("removes all pending image pull steps") {
                    verify(context).removePendingStepsOfType(PullImageStep::class)
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

            val logger = Logger("test.source", InMemoryLogSink())

            describe("when cleanup after failure is enabled") {
                on("when the task network has not been created yet") {
                    val context = mock<TaskEventContext> {
                        on { behaviourAfterFailure } doReturn BehaviourAfterFailure.Cleanup
                        on { getPastEventsOfType<ContainerCreatedEvent>() } doReturn emptySet<ContainerCreatedEvent>()
                        on { getSinglePastEventOfType<TaskNetworkCreatedEvent>() } doReturn null as TaskNetworkCreatedEvent?
                    }

                    event.apply(context, logger)

                    it("aborts the task") {
                        verify(context).abort()
                    }

                    it("queues a step to display an error message to the user") {
                        verify(context).queueStep(DisplayTaskFailureStep("This is the message to display"))
                    }

                    itRemovesAllPendingPreTaskSteps(context)
                }

                on("when the task network has been created but no containers have been created") {
                    val network = DockerNetwork("the-id")
                    val context = mock<TaskEventContext> {
                        on { behaviourAfterFailure } doReturn BehaviourAfterFailure.Cleanup
                        on { getPastEventsOfType<ContainerCreatedEvent>() } doReturn emptySet<ContainerCreatedEvent>()
                        on { getSinglePastEventOfType<TaskNetworkCreatedEvent>() } doReturn TaskNetworkCreatedEvent(network)
                    }

                    event.apply(context, logger)

                    it("aborts the task") {
                        verify(context).abort()
                    }

                    it("queues a step to display an error message to the user") {
                        verify(context).queueStep(DisplayTaskFailureStep("This is the message to display"))
                    }

                    itRemovesAllPendingPreTaskSteps(context)

                    it("queues a 'delete task network' step") {
                        verify(context).queueStep(DeleteTaskNetworkStep(network))
                    }
                }

                on("when the task network has been created and some containers have been created") {
                    val network = DockerNetwork("the-id")
                    val container1 = Container("container-1", imageSourceDoesNotMatter())
                    val container2 = Container("container-2", imageSourceDoesNotMatter())
                    val dockerContainer1 = DockerContainer("docker-container-1")
                    val dockerContainer2 = DockerContainer("docker-container-2")

                    val context = mock<TaskEventContext> {
                        on { behaviourAfterFailure } doReturn BehaviourAfterFailure.Cleanup
                        on { getPastEventsOfType<ContainerCreatedEvent>() } doReturn setOf(
                            ContainerCreatedEvent(container1, dockerContainer1),
                            ContainerCreatedEvent(container2, dockerContainer2)
                        )
                        on { getSinglePastEventOfType<TaskNetworkCreatedEvent>() } doReturn TaskNetworkCreatedEvent(network)
                    }

                    event.apply(context, logger)

                    it("aborts the task") {
                        verify(context).abort()
                    }

                    it("queues a step to display an error message to the user") {
                        verify(context).queueStep(DisplayTaskFailureStep("This is the message to display"))
                    }

                    itRemovesAllPendingPreTaskSteps(context)

                    it("queues a 'clean up container' step for any container that has already been created") {
                        verify(context).queueStep(CleanUpContainerStep(container1, dockerContainer1))
                        verify(context).queueStep(CleanUpContainerStep(container2, dockerContainer2))
                    }

                    it("does not queue a 'delete task network' step") {
                        verify(context, never()).queueStep(any<DeleteTaskNetworkStep>())
                    }
                }
            }

            describe("when cleanup after failure is disabled") {
                on("when the task network has not been created yet") {
                    val context = mock<TaskEventContext> {
                        on { behaviourAfterFailure } doReturn BehaviourAfterFailure.DontCleanup
                        on { getPastEventsOfType<ContainerCreatedEvent>() } doReturn emptySet<ContainerCreatedEvent>()
                        on { getSinglePastEventOfType<TaskNetworkCreatedEvent>() } doReturn null as TaskNetworkCreatedEvent?
                    }

                    event.apply(context, logger)

                    it("aborts the task") {
                        verify(context).abort()
                    }

                    it("queues a step to display an error message to the user") {
                        verify(context).queueStep(DisplayTaskFailureStep("This is the message to display"))
                    }

                    itRemovesAllPendingPreTaskSteps(context)
                }

                on("when the task network has been created but no containers have been created") {
                    val network = DockerNetwork("the-id")
                    val context = mock<TaskEventContext> {
                        on { behaviourAfterFailure } doReturn BehaviourAfterFailure.DontCleanup
                        on { getPastEventsOfType<ContainerCreatedEvent>() } doReturn emptySet<ContainerCreatedEvent>()
                        on { getSinglePastEventOfType<TaskNetworkCreatedEvent>() } doReturn TaskNetworkCreatedEvent(network)
                    }

                    event.apply(context, logger)

                    it("aborts the task") {
                        verify(context).abort()
                    }

                    it("queues a step to display an error message to the user") {
                        verify(context).queueStep(DisplayTaskFailureStep("This is the message to display"))
                    }

                    itRemovesAllPendingPreTaskSteps(context)

                    it("queues a 'delete task network' step") {
                        verify(context).queueStep(DeleteTaskNetworkStep(network))
                    }
                }

                on("when the task network has been created and some containers have been created") {
                    val network = DockerNetwork("the-id")
                    val container1 = Container("container-1", imageSourceDoesNotMatter())
                    val container2 = Container("container-2", imageSourceDoesNotMatter())
                    val container3 = Container("container-3", imageSourceDoesNotMatter())
                    val dockerContainer1 = DockerContainer("docker-container-1")
                    val dockerContainer2 = DockerContainer("docker-container-2")
                    val dockerContainer3 = DockerContainer("docker-container-3")

                    val context = mock<TaskEventContext> {
                        on { behaviourAfterFailure } doReturn BehaviourAfterFailure.DontCleanup

                        on { getPastEventsOfType<ContainerCreatedEvent>() } doReturn setOf(
                            ContainerCreatedEvent(container1, dockerContainer1),
                            ContainerCreatedEvent(container2, dockerContainer2),
                            ContainerCreatedEvent(container3, dockerContainer3)
                        )

                        on { getPastEventsOfType<ContainerStartedEvent>() } doReturn setOf(
                            ContainerStartedEvent(container1),
                            ContainerStartedEvent(container2)
                        )

                        on { getSinglePastEventOfType<TaskNetworkCreatedEvent>() } doReturn TaskNetworkCreatedEvent(network)
                    }

                    event.apply(context, logger)

                    it("aborts the task") {
                        verify(context).abort()
                    }

                    it("queues a step to display an error message to the user with information on how to view logs from the containers that started and remove them") {
                        val expectedMessage = """
                            This is the message to display

                            As the task was run with --no-cleanup-after-failure, the created containers will not be cleaned up.

                            You can view the logs for container 'container-1' by running 'docker logs docker-container-1'.
                            You can view the logs for container 'container-2' by running 'docker logs docker-container-2'.

                            To clean up the containers and task network once you have finished investigating the issue, run 'docker rm --force docker-container-1 docker-container-2 docker-container-3 && docker network rm the-id'.
                        """.trimIndent()

                        verify(context).queueStep(DisplayTaskFailureStep(expectedMessage))
                    }

                    itRemovesAllPendingPreTaskSteps(context)

                    it("does not queue a 'clean up container' step") {
                        verify(context, never()).queueStep(any<CleanUpContainerStep>())
                    }

                    it("does not queue a 'delete task network' step") {
                        verify(context, never()).queueStep(any<DeleteTaskNetworkStep>())
                    }
                }
            }
        }
    }
})
