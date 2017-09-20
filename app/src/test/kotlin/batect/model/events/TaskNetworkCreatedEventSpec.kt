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

import batect.config.BuildImage
import batect.config.Container
import batect.config.PullImage
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.model.steps.CreateContainerStep
import batect.model.steps.DeleteTaskNetworkStep
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TaskNetworkCreatedEventSpec : Spek({
    describe("a 'task network created' event") {
        val network = DockerNetwork("some-network")
        val event = TaskNetworkCreatedEvent(network)

        val containerWithImageToBuild = Container("container-1", BuildImage("/container-1-build-dir"))
        val image1 = DockerImage("image-1")

        val containerWithImageToPull = Container("container-2", PullImage("image-2"))
        val image2 = DockerImage("image-2")

        describe("being applied") {
            on("when no images have been built or pulled yet") {
                val context = mock<TaskEventContext> { }
                event.apply(context)

                it("does not queue any further work") {
                    verify(context, never()).queueStep(any())
                }
            }

            on("when some images have been built or pulled already") {
                val context = mock<TaskEventContext> {
                    on { getPastEventsOfType<ImageBuiltEvent>() } doReturn setOf(
                        ImageBuiltEvent(containerWithImageToBuild, image1)
                    )

                    on { getPastEventsOfType<ImagePulledEvent>() } doReturn setOf(
                        ImagePulledEvent(image2)
                    )

                    on { commandForContainer(containerWithImageToBuild) } doReturn "command-1"
                    on { commandForContainer(containerWithImageToPull) } doReturn "command-2"

                    on { allTaskContainers } doReturn setOf(containerWithImageToBuild, containerWithImageToPull)
                }

                event.apply(context)

                it("queues 'create container' steps for them") {
                    verify(context).queueStep(CreateContainerStep(containerWithImageToBuild, "command-1", image1, network))
                    verify(context).queueStep(CreateContainerStep(containerWithImageToPull, "command-2", image2, network))
                }

                it("does not queue a 'delete task network' step") {
                    verify(context, never()).queueStep(any<DeleteTaskNetworkStep>())
                }
            }

            on("when the task is aborting") {
                val context = mock<TaskEventContext> {
                    on { isAborting } doReturn true
                    on { getPastEventsOfType<ImageBuiltEvent>() } doReturn setOf(
                        ImageBuiltEvent(containerWithImageToBuild, image1)
                    )
                    on { getPastEventsOfType<ImagePulledEvent>() } doReturn setOf(
                        ImagePulledEvent(image2)
                    )
                }

                event.apply(context)

                it("queues a 'delete task network' step") {
                    verify(context).queueStep(DeleteTaskNetworkStep(network))
                }

                it("does not queue any 'create container' steps for the containers with built or pulled images") {
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
