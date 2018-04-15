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

package batect.execution.model.rules.cleanup

import batect.config.Container
import batect.execution.model.events.ContainerRemovedEvent
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.DeleteTemporaryFileStep
import batect.testutils.equalTo
import batect.testutils.imageSourceDoesNotMatter
import com.natpryce.hamkrest.assertion.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.nio.file.Paths

object DeleteTemporaryFileStepRuleSpec : Spek({
    describe("a delete temporary file step rule") {
        val path = Paths.get("/some-file")

        given("there is a container that must be removed first") {
            val container = Container("the-container", imageSourceDoesNotMatter())
            val rule = DeleteTemporaryFileStepRule(path, container)

            given("the container has been removed") {
                val events = setOf(
                    ContainerRemovedEvent(container)
                )

                on("evaluating the rule") {
                    val result = rule.evaluate(events)

                    it("returns a 'delete temporary file' step") {
                        assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(DeleteTemporaryFileStep(path))))
                    }
                }
            }

            given("another container has been removed") {
                val events = setOf(
                    ContainerRemovedEvent(Container("some-other-container", imageSourceDoesNotMatter()))
                )

                on("evaluating the rule") {
                    val result = rule.evaluate(events)

                    it("indicates that the step is not yet ready") {
                        assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                    }
                }
            }

            given("the container has not been removed") {
                on("evaluating the rule") {
                    val result = rule.evaluate(emptySet())

                    it("indicates that the step is not yet ready") {
                        assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                    }
                }
            }
        }

        given("there is no container that must be removed first") {
            val rule = DeleteTemporaryFileStepRule(path, null)

            on("evaluating the rule") {
                val result = rule.evaluate(emptySet())

                it("returns a 'delete temporary file' step") {
                    assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(DeleteTemporaryFileStep(path))))
                }
            }
        }

        on("getting the manual cleanup instruction") {
            val rule = DeleteTemporaryFileStepRule(path, null)
            val instruction = rule.manualCleanupInstruction

            it("returns the appropriate CLI command to use") {
                assertThat(instruction, equalTo("rm /some-file"))
            }
        }
    }
})
