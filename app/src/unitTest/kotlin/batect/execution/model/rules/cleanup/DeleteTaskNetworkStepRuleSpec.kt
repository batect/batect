/*
    Copyright 2017-2022 Charles Korn.

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
import batect.dockerclient.NetworkReference
import batect.execution.model.events.ContainerRemovedEvent
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.DeleteTaskNetworkStep
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.logRepresentationOf
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DeleteTaskNetworkStepRuleSpec : Spek({
    describe("a delete task network step rule") {
        val network = NetworkReference("the-network")

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
            val instruction = rule.manualCleanupCommand

            it("returns the appropriate Docker CLI command to use") {
                assertThat(instruction, equalTo("docker network rm the-network"))
            }
        }

        on("attaching it to a log message") {
            val container1 = Container("container-1", imageSourceDoesNotMatter())
            val container2 = Container("container-2", imageSourceDoesNotMatter())
            val rule = DeleteTaskNetworkStepRule(network, setOf(container1, container2))

            it("returns a machine-readable representation of itself") {
                assertThat(
                    logRepresentationOf(rule),
                    equivalentTo(
                        """
                        |{
                        |   "type": "${rule::class.qualifiedName}",
                        |   "network": {"id": "the-network"},
                        |   "containersThatMustBeRemovedFirst": ["container-1", "container-2"],
                        |   "manualCleanupCommand": "docker network rm the-network"
                        |}
                        """.trimMargin()
                    )
                )
            }
        }
    }
})
