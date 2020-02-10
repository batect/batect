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
import batect.execution.model.steps.RemoveContainerStep
import batect.os.OperatingSystem
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RemoveContainerStepRuleSpec : Spek({
    describe("a remove container step rule") {
        val containerToRemove = Container("the-container", imageSourceDoesNotMatter())
        val dockerContainerToRemove = DockerContainer("some-container-id")

        given("the container was started") {
            val rule = RemoveContainerStepRule(containerToRemove, dockerContainerToRemove, true)

            given("the container has been stopped") {
                val events = setOf(
                    ContainerStoppedEvent(containerToRemove)
                )

                on("evaluating the rule") {
                    val result = rule.evaluate(events)

                    it("returns a 'remove container' step") {
                        assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(RemoveContainerStep(containerToRemove, dockerContainerToRemove))))
                    }
                }
            }

            given("another container has been stopped") {
                val events = setOf(
                    ContainerStoppedEvent(Container("some-other-container", imageSourceDoesNotMatter()))
                )

                on("evaluating the rule") {
                    val result = rule.evaluate(events)

                    it("indicates that the step is not yet ready") {
                        assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                    }
                }
            }

            given("the container has not been stopped") {
                on("evaluating the rule") {
                    val result = rule.evaluate(emptySet())

                    it("indicates that the step is not yet ready") {
                        assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                    }
                }
            }
        }

        given("the container was not started") {
            val rule = RemoveContainerStepRule(containerToRemove, dockerContainerToRemove, false)

            on("evaluating the rule") {
                val result = rule.evaluate(emptySet())

                it("returns a 'remove container' step") {
                    assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(RemoveContainerStep(containerToRemove, dockerContainerToRemove))))
                }
            }
        }

        on("getting the manual cleanup instruction") {
            val rule = RemoveContainerStepRule(containerToRemove, dockerContainerToRemove, true)
            val instruction = rule.getManualCleanupInstructionForOperatingSystem(OperatingSystem.Other)

            it("returns the appropriate Docker CLI command to use") {
                assertThat(instruction, equalTo("docker rm --force --volumes some-container-id"))
            }
        }

        on("toString()") {
            val rule = RemoveContainerStepRule(containerToRemove, dockerContainerToRemove, true)

            it("returns a human-readable representation of itself") {
                assertThat(rule.toString(), equalTo("RemoveContainerStepRule(container: 'the-container', Docker container: 'some-container-id', container was started: true)"))
            }
        }
    }
})
