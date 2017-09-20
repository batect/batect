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
import batect.model.steps.CleanUpContainerStep
import batect.model.steps.RunContainerStep
import batect.model.steps.StartContainerStep
import batect.config.Container
import batect.docker.DockerContainer
import batect.testutils.imageSourceDoesNotMatter
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ContainerCreatedEventSpec : Spek({
    describe("a 'container created' event") {
        val dependency1 = Container("dependency-container-1", imageSourceDoesNotMatter())
        val dependency2 = Container("dependency-container-2", imageSourceDoesNotMatter())

        val container = Container("container-1", imageSourceDoesNotMatter(), dependencies = setOf(dependency1.name, dependency2.name))
        val dockerContainer = DockerContainer("docker-container-1")
        val event = ContainerCreatedEvent(container, dockerContainer)

        describe("being applied") {
            describe("when all of the container's dependencies are healthy") {
                on("when the container is the task container") {
                    val context = mock<TaskEventContext> {
                        on { isTaskContainer(container) } doReturn true
                        on { dependenciesOf(container) } doReturn setOf(
                                dependency1,
                                dependency2
                        )
                        on { getPastEventsOfType<ContainerBecameHealthyEvent>() } doReturn setOf(
                                ContainerBecameHealthyEvent(dependency1),
                                ContainerBecameHealthyEvent(dependency2)
                        )
                    }

                    event.apply(context)

                    it("queues a 'run container' step") {
                        verify(context).queueStep(RunContainerStep(container, dockerContainer))
                    }
                }

                on("when the container is a dependency container") {
                    val context = mock<TaskEventContext> {
                        on { isTaskContainer(container) } doReturn false
                        on { dependenciesOf(container) } doReturn setOf(
                                dependency1,
                                dependency2
                        )
                        on { getPastEventsOfType<ContainerBecameHealthyEvent>() } doReturn setOf(
                                ContainerBecameHealthyEvent(dependency1),
                                ContainerBecameHealthyEvent(dependency2)
                        )
                    }

                    event.apply(context)

                    it("queues a 'start container' step") {
                        verify(context).queueStep(StartContainerStep(container, dockerContainer))
                    }
                }
            }

            on("when not all of the container's dependencies are healthy yet") {
                val context = mock<TaskEventContext> {
                    on { dependenciesOf(container) } doReturn setOf(
                            dependency1,
                            dependency2
                    )
                    on { getPastEventsOfType<ContainerBecameHealthyEvent>() } doReturn setOf(
                            ContainerBecameHealthyEvent(dependency1)
                    )
                }

                event.apply(context)

                it("does not queue any further work") {
                    verify(context, never()).queueStep(any())
                }
            }

            on("when the task is aborting") {
                val context = mock<TaskEventContext> {
                    on { isAborting } doReturn true
                }

                event.apply(context)

                it("queues a 'clean up container' step") {
                    verify(context).queueStep(CleanUpContainerStep(container, dockerContainer))
                }
            }
        }

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assertThat(event.toString(), equalTo("ContainerCreatedEvent(container: 'container-1', Docker container ID: 'docker-container-1')"))
            }
        }
    }
})
