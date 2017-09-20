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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import batect.model.steps.WaitForContainerToBecomeHealthyStep
import batect.config.Container
import batect.docker.DockerContainer
import batect.testutils.imageSourceDoesNotMatter
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ContainerStartedEventSpec : Spek({
    describe("a 'container started' event") {
        val container = Container("container-1", imageSourceDoesNotMatter())
        val event = ContainerStartedEvent(container)

        describe("being applied") {
            on("when the task is not aborting") {
                val dockerContainer = DockerContainer("container-1-dc")
                val otherContainer = Container("container-2", imageSourceDoesNotMatter())
                val otherDockerContainer = DockerContainer("container-2-dc")

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
                assertThat(event.toString(), equalTo("ContainerStartedEvent(container: 'container-1')"))
            }
        }
    }
})
