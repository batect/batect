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
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import batect.model.steps.CreateContainerStep
import batect.config.Container
import batect.config.PortMapping
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.logging.Logger
import batect.os.Command
import batect.testutils.InMemoryLogSink
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ImageBuiltEventSpec : Spek({
    describe("an 'image built' event") {
        val container = Container("container-1", BuildImage("/container-1-build-dir"))
        val image = DockerImage("image-1")
        val event = ImageBuiltEvent(container, image)

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
                val additionalEnvironmentVariables = mapOf("SOME_VAR" to "some value")
                val additionalPortMappings = setOf(PortMapping(123, 456))
                val network = DockerNetwork("the-network")
                val command = Command.parse("do-stuff")

                val context = mock<TaskEventContext> {
                    on { getSinglePastEventOfType<TaskNetworkCreatedEvent>() } doReturn TaskNetworkCreatedEvent(network)
                    on { commandForContainer(container) } doReturn command
                    on { additionalEnvironmentVariablesForContainer(container) } doReturn additionalEnvironmentVariables
                    on { additionalPortMappingsForContainer(container) } doReturn additionalPortMappings
                }

                event.apply(context, logger)

                it("queues a 'create container' step") {
                    verify(context).queueStep(CreateContainerStep(container, command, additionalEnvironmentVariables, additionalPortMappings, image, network))
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
                com.natpryce.hamkrest.assertion.assertThat(event.toString(), equalTo("ImageBuiltEvent(container: 'container-1', image: 'image-1')"))
            }
        }
    }
})
