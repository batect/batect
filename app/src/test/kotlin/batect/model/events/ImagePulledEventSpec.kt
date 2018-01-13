/*
   Copyright 2017-2018 Charles Korn.

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
import batect.logging.Logger
import batect.model.steps.CreateContainerStep
import batect.os.Command
import batect.testutils.InMemoryLogSink
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ImagePulledEventSpec : Spek({
    describe("an 'image pulled' event") {
        val image = DockerImage("image-1")
        val event = ImagePulledEvent(image)

        describe("being applied") {
            val logger = Logger("test.source", InMemoryLogSink())

            on("when the task network has not been created yet") {
                val context = mock<TaskEventContext> {
                    on { getSinglePastEventOfType<TaskNetworkCreatedEvent>() } doReturn null as TaskNetworkCreatedEvent?
                }

                event.apply(context, logger)

                it("does not queue any further work") {
                    verify(context, never()).queueStep(any())
                }
            }

            on("when the task network has already been created") {
                val containerWithImageToBuild = Container("container-1", BuildImage("/container-1-build-dir"))
                val containerWithThisImage1 = Container("container-2", PullImage(image.id))
                val containerWithThisImage2 = Container("container-3", PullImage(image.id))
                val containerWithAnotherImageToPull = Container("container-4", PullImage("other-image"))
                val additionalEnvironmentVariablesForContainer1 = mapOf("SOME_VAR" to "some value")
                val command1 = Command.parse("command-1")
                val command2 = Command.parse("command-2")

                val network = DockerNetwork("the-network")
                val context = mock<TaskEventContext> {
                    on { getSinglePastEventOfType<TaskNetworkCreatedEvent>() } doReturn TaskNetworkCreatedEvent(network)
                    on { commandForContainer(containerWithThisImage1) } doReturn command1
                    on { additionalEnvironmentVariablesForContainer(containerWithThisImage1) } doReturn additionalEnvironmentVariablesForContainer1

                    on { commandForContainer(containerWithThisImage2) } doReturn command2
                    on { additionalEnvironmentVariablesForContainer(containerWithThisImage2) } doReturn emptyMap()

                    on { allTaskContainers } doReturn setOf(
                        containerWithImageToBuild, containerWithThisImage1, containerWithThisImage2, containerWithAnotherImageToPull
                    )
                }

                event.apply(context, logger)

                it("queues a 'create container' step for each container that requires the image") {
                    verify(context).queueStep(CreateContainerStep(containerWithThisImage1, command1, additionalEnvironmentVariablesForContainer1, image, network))
                    verify(context).queueStep(CreateContainerStep(containerWithThisImage2, command2, emptyMap(), image, network))
                }

                it("does not queue any other work") {
                    verify(context, times(2)).queueStep(any())
                }
            }

            on("when the task is aborting") {
                val context = mock<TaskEventContext> {
                    on { isAborting } doReturn true
                }

                event.apply(context, logger)

                it("does not queue any further work") {
                    verify(context, never()).queueStep(any())
                }
            }
        }

        on("toString()") {
            it("returns a human-readable representation of itself") {
                com.natpryce.hamkrest.assertion.assertThat(event.toString(), equalTo("ImagePulledEvent(image: 'image-1')"))
            }
        }
    }
})
