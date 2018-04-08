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

package batect.model.rules.run

import batect.config.Container
import batect.docker.DockerContainer
import batect.model.events.ContainerBecameHealthyEvent
import batect.model.events.ContainerCreatedEvent
import batect.model.events.TaskEvent
import batect.model.rules.TaskStepRuleEvaluationResult
import batect.model.steps.StartContainerStep
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.imageSourceDoesNotMatter
import com.natpryce.hamkrest.assertion.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object StartContainerStepRuleSpec : Spek({
    describe("a start container step rule") {
        given("the container has no dependencies") {
            val container = Container("the-container", imageSourceDoesNotMatter())
            val dependencies = emptySet<Container>()
            val rule = StartContainerStepRule(container, dependencies)

            given("the container has been created") {
                val dockerContainer = DockerContainer("some-created-container")
                val events = setOf(ContainerCreatedEvent(container, dockerContainer))

                on("evaluating the rule") {
                    val result = rule.evaluate(events)

                    it("returns a 'start container' step") {
                        assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(StartContainerStep(
                            container,
                            dockerContainer
                        ))))
                    }
                }
            }

            given("another container has been created") {
                val otherContainer = Container("some-other-container", imageSourceDoesNotMatter())
                val dockerContainer = DockerContainer("some-created-container")
                val events = setOf(ContainerCreatedEvent(otherContainer, dockerContainer))

                on("evaluating the rule") {
                    val result = rule.evaluate(events)

                    it("indicates that the step is not yet ready") {
                        assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                    }
                }
            }

            given("the container has not been created") {
                val events = emptySet<TaskEvent>()

                on("evaluating the rule") {
                    val result = rule.evaluate(events)

                    it("indicates that the step is not yet ready") {
                        assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                    }
                }
            }
        }

        given("the container has some dependencies") {
            val container = Container("the-container", imageSourceDoesNotMatter())
            val dependency1 = Container("dependency-1", imageSourceDoesNotMatter())
            val dependency2 = Container("dependency-2", imageSourceDoesNotMatter())
            val dependencies = setOf(dependency1, dependency2)
            val rule = StartContainerStepRule(container, dependencies)
            val events by createForEachTest { mutableSetOf<TaskEvent>() }

            given("the container has been created") {
                val dockerContainer = DockerContainer("some-created-container")
                beforeEachTest { events.add(ContainerCreatedEvent(container, dockerContainer)) }

                given("none of the dependencies have reported as healthy") {
                    on("evaluating the rule") {
                        val result = rule.evaluate(events)

                        it("indicates that the step is not yet ready") {
                            assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                        }
                    }
                }

                given("some of the dependencies have reported as healthy") {
                    beforeEachTest { events.add(ContainerBecameHealthyEvent(dependency1)) }

                    on("evaluating the rule") {
                        val result = rule.evaluate(events)

                        it("indicates that the step is not yet ready") {
                            assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                        }
                    }
                }

                given("all of the dependencies have reported as healthy") {
                    beforeEachTest { events.add(ContainerBecameHealthyEvent(dependency1)) }
                    beforeEachTest { events.add(ContainerBecameHealthyEvent(dependency2)) }

                    on("evaluating the rule") {
                        val result = rule.evaluate(events)

                        it("returns a 'start container' step") {
                            assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(StartContainerStep(
                                container,
                                dockerContainer
                            ))))
                        }
                    }
                }
            }

            given("the container has not been created") {
                on("evaluating the rule") {
                    val result = rule.evaluate(events)

                    it("indicates that the step is not yet ready") {
                        assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                    }
                }
            }
        }
    }
})
