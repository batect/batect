/*
   Copyright 2017-2020 Charles Korn.

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
import batect.docker.DockerContainer
import batect.execution.model.events.ContainerStoppedEvent
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.StopContainerStep
import batect.os.OperatingSystem
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.logRepresentationOf
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object StopContainerStepRuleSpec : Spek({
    describe("a stop container step rule") {
        val containerToStop = Container("the-container", imageSourceDoesNotMatter())
        val dockerContainerToStop = DockerContainer("some-container-id")

        given("there are no containers that must be stopped first") {
            val rule = StopContainerStepRule(containerToStop, dockerContainerToStop, emptySet())

            on("evaluating the rule") {
                val result = rule.evaluate(emptySet())

                it("returns a 'stop container' step") {
                    assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(StopContainerStep(containerToStop, dockerContainerToStop))))
                }
            }

            on("attaching it to a log message") {
                it("returns a machine-readable representation of itself") {
                    assertThat(logRepresentationOf(rule), equivalentTo("""
                        |{
                        |   "type": "${rule::class.qualifiedName}",
                        |   "container": "the-container",
                        |   "dockerContainer": {"id": "some-container-id", "name": null},
                        |   "containersThatMustBeStoppedFirst": []
                        |}
                    """.trimMargin()))
                }
            }
        }

        given("there are some containers that must be stopped first") {
            val container1 = Container("container-1", imageSourceDoesNotMatter())
            val container2 = Container("container-2", imageSourceDoesNotMatter())
            val rule = StopContainerStepRule(containerToStop, dockerContainerToStop, setOf(container1, container2))

            given("those containers have been stopped") {
                val events = setOf(
                    ContainerStoppedEvent(container1),
                    ContainerStoppedEvent(container2)
                )

                on("evaluating the rule") {
                    val result = rule.evaluate(events)

                    it("returns a 'stop container' step") {
                        assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(StopContainerStep(containerToStop, dockerContainerToStop))))
                    }
                }
            }

            given("those containers have not been stopped") {
                on("evaluating the rule") {
                    val result = rule.evaluate(emptySet())

                    it("indicates that the step is not yet ready") {
                        assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                    }
                }
            }

            on("attaching it to a log message") {
                it("returns a machine-readable representation of itself") {
                    assertThat(logRepresentationOf(rule), equivalentTo("""
                        |{
                        |   "type": "${rule::class.qualifiedName}",
                        |   "container": "the-container",
                        |   "dockerContainer": {"id": "some-container-id", "name": null},
                        |   "containersThatMustBeStoppedFirst": ["container-1", "container-2"]
                        |}
                    """.trimMargin()))
                }
            }
        }

        on("getting the manual cleanup instruction") {
            val rule = StopContainerStepRule(containerToStop, dockerContainerToStop, emptySet())
            val instruction = rule.getManualCleanupInstructionForOperatingSystem(OperatingSystem.Other)

            it("returns no instruction, since it will be covered by the removal rule's instruction") {
                assertThat(instruction, equalTo(null))
            }
        }
    }
})
