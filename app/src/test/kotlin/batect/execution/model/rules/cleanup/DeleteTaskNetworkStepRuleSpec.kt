/*
   Copyright 2017-2019 Charles Korn.

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

package batect.execution.model.rules.cleanup

import batect.config.Container
import batect.docker.DockerNetwork
import batect.execution.model.events.ContainerRemovedEvent
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.DeleteTaskNetworkStep
import batect.testutils.equalTo
import batect.testutils.imageSourceDoesNotMatter
import com.natpryce.hamkrest.assertion.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object DeleteTaskNetworkStepRuleSpec : Spek({
    describe("a delete task network step rule") {
        val network = DockerNetwork("the-network")

        given("there are no containers that must be removed first") {
            val rule = DeleteTaskNetworkStepRule(network, emptySet())

            on("evaluating the rule") {
                val result = rule.evaluate(emptySet())

                it("returns a 'delete task network' step") {
                    assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(DeleteTaskNetworkStep(network))))
                }
            }
        }

        given("there are some containers that must be removed first") {
            val container1 = Container("container-1", imageSourceDoesNotMatter())
            val container2 = Container("container-2", imageSourceDoesNotMatter())
            val rule = DeleteTaskNetworkStepRule(network, setOf(container1, container2))

            given("those containers have been removed") {
                val events = setOf(
                    ContainerRemovedEvent(container1),
                    ContainerRemovedEvent(container2)
                )

                on("evaluating the rule") {
                    val result = rule.evaluate(events)

                    it("returns a 'delete task network' step") {
                        assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(DeleteTaskNetworkStep(network))))
                    }
                }
            }

            given("those containers have not been removed") {
                on("evaluating the rule") {
                    val result = rule.evaluate(emptySet())

                    it("indicates that the step is not yet ready") {
                        assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                    }
                }
            }
        }

        on("getting the manual cleanup instruction") {
            val rule = DeleteTaskNetworkStepRule(network, emptySet())
            val instruction = rule.manualCleanupInstruction

            it("returns the appropriate Docker CLI command to use") {
                assertThat(instruction, equalTo("docker network rm the-network"))
            }
        }

        on("toString()") {
            val container1 = Container("container-1", imageSourceDoesNotMatter())
            val container2 = Container("container-2", imageSourceDoesNotMatter())
            val rule = DeleteTaskNetworkStepRule(network, setOf(container1, container2))

            it("returns a human-readable representation of itself") {
                assertThat(rule.toString(), equalTo("DeleteTaskNetworkStepRule(network: 'the-network', containers that must be removed first: ['container-1', 'container-2'])"))
            }
        }
    }
})
