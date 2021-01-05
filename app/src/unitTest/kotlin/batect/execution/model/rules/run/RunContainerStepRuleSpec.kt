/*
   Copyright 2017-2021 Charles Korn.

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

package batect.execution.model.rules.run

import batect.config.Container
import batect.docker.DockerContainer
import batect.execution.model.events.ContainerBecameReadyEvent
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.RunContainerStep
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.logRepresentationOf
import batect.testutils.on
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RunContainerStepRuleSpec : Spek({
    describe("a run container step rule") {
        given("the container has no dependencies") {
            val container = Container("the-container", imageSourceDoesNotMatter())
            val dependencies = emptySet<Container>()
            val rule = RunContainerStepRule(container, dependencies)

            given("the container has been created") {
                val dockerContainer = DockerContainer("some-created-container")
                val events = setOf(ContainerCreatedEvent(container, dockerContainer))

                on("evaluating the rule") {
                    val result by runForEachTest { rule.evaluate(events) }

                    it("returns a 'run container' step") {
                        assertThat(
                            result,
                            equalTo(
                                TaskStepRuleEvaluationResult.Ready(
                                    RunContainerStep(
                                        container,
                                        dockerContainer
                                    )
                                )
                            )
                        )
                    }
                }
            }

            given("another container has been created") {
                val otherContainer = Container("some-other-container", imageSourceDoesNotMatter())
                val dockerContainer = DockerContainer("some-created-container")
                val events = setOf(ContainerCreatedEvent(otherContainer, dockerContainer))

                on("evaluating the rule") {
                    val result by runForEachTest { rule.evaluate(events) }

                    it("indicates that the step is not yet ready") {
                        assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                    }
                }
            }

            given("the container has not been created") {
                val events = emptySet<TaskEvent>()

                on("evaluating the rule") {
                    val result by runForEachTest { rule.evaluate(events) }

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
            val rule = RunContainerStepRule(container, dependencies)
            val events by createForEachTest { mutableSetOf<TaskEvent>() }

            given("the container has been created") {
                val dockerContainer = DockerContainer("some-created-container")
                beforeEachTest { events.add(ContainerCreatedEvent(container, dockerContainer)) }

                given("none of the dependencies have reported as ready") {
                    on("evaluating the rule") {
                        val result by runForEachTest { rule.evaluate(events) }

                        it("indicates that the step is not yet ready") {
                            assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                        }
                    }
                }

                given("some of the dependencies have reported as ready") {
                    beforeEachTest { events.add(ContainerBecameReadyEvent(dependency1)) }

                    on("evaluating the rule") {
                        val result by runForEachTest { rule.evaluate(events) }

                        it("indicates that the step is not yet ready") {
                            assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                        }
                    }
                }

                given("all of the dependencies have reported as ready") {
                    beforeEachTest { events.add(ContainerBecameReadyEvent(dependency1)) }
                    beforeEachTest { events.add(ContainerBecameReadyEvent(dependency2)) }

                    on("evaluating the rule") {
                        val result by runForEachTest { rule.evaluate(events) }

                        it("returns a 'run container' step") {
                            assertThat(
                                result,
                                equalTo(
                                    TaskStepRuleEvaluationResult.Ready(
                                        RunContainerStep(
                                            container,
                                            dockerContainer
                                        )
                                    )
                                )
                            )
                        }
                    }
                }
            }

            given("the container has not been created") {
                on("evaluating the rule") {
                    val result by runForEachTest { rule.evaluate(events) }

                    it("indicates that the step is not yet ready") {
                        assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                    }
                }
            }
        }

        on("attaching it to a log message") {
            val container = Container("the-container", imageSourceDoesNotMatter())
            val dependency1 = Container("dependency-1", imageSourceDoesNotMatter())
            val dependency2 = Container("dependency-2", imageSourceDoesNotMatter())
            val dependencies = setOf(dependency1, dependency2)
            val rule = RunContainerStepRule(container, dependencies)

            it("returns a machine-readable representation of itself") {
                assertThat(
                    logRepresentationOf(rule),
                    equivalentTo(
                        """
                        |{
                        |   "type": "${rule::class.qualifiedName}",
                        |   "container": "the-container",
                        |   "dependencies": ["dependency-1", "dependency-2"]
                        |}
                        """.trimMargin()
                    )
                )
            }
        }
    }
})
