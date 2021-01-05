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

package batect.execution.model.rules.cleanup

import batect.config.Container
import batect.execution.model.events.ContainerRemovedEvent
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.DeleteTemporaryDirectoryStep
import batect.os.OperatingSystem
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.logRepresentationOf
import batect.testutils.on
import batect.testutils.osIndependentPath
import com.natpryce.hamkrest.assertion.assertThat
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DeleteTemporaryDirectoryStepRuleSpec : Spek({
    describe("a delete temporary directory step rule") {
        val path = osIndependentPath("/some-directory")

        given("there is a container that must be removed first") {
            val container = Container("the-container", imageSourceDoesNotMatter())
            val rule = DeleteTemporaryDirectoryStepRule(path, container)

            given("the container has been removed") {
                val events = setOf(
                    ContainerRemovedEvent(container)
                )

                on("evaluating the rule") {
                    val result = rule.evaluate(events)

                    it("returns a 'delete temporary directory' step") {
                        assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(DeleteTemporaryDirectoryStep(path))))
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

            on("attaching it to a log message") {
                it("returns a machine-readable representation of itself") {
                    assertThat(
                        logRepresentationOf(rule),
                        equivalentTo(
                            """
                            |{
                            |   "type": "${rule::class.qualifiedName}",
                            |   "path": "/some-directory",
                            |   "containerThatMustBeRemovedFirst": "the-container"
                            |}
                            """.trimMargin()
                        )
                    )
                }
            }
        }

        given("there is no container that must be removed first") {
            val rule = DeleteTemporaryDirectoryStepRule(path, null)

            on("evaluating the rule") {
                val result = rule.evaluate(emptySet())

                it("returns a 'delete temporary directory' step") {
                    assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(DeleteTemporaryDirectoryStep(path))))
                }
            }

            on("attaching it to a log message") {
                it("returns a machine-readable representation of itself") {
                    assertThat(
                        logRepresentationOf(rule),
                        equivalentTo(
                            """
                            |{
                            |   "type": "${rule::class.qualifiedName}",
                            |   "path": "/some-directory",
                            |   "containerThatMustBeRemovedFirst": null
                            |}
                            """.trimMargin()
                        )
                    )
                }
            }
        }

        describe("getting the manual cleanup instruction") {
            val rule = DeleteTemporaryDirectoryStepRule(path, null)

            given("the application is running on Windows") {
                val instruction = rule.getManualCleanupInstructionForOperatingSystem(OperatingSystem.Windows)

                it("returns the appropriate CLI command to use") {
                    assertThat(instruction, equalTo("Remove-Item -Recurse /some-directory (if using PowerShell) or rmdir /s /q /some-directory (if using Command Prompt)"))
                }
            }

            given("the application is not running on Windows") {
                val instruction = rule.getManualCleanupInstructionForOperatingSystem(OperatingSystem.Other)

                it("returns the appropriate CLI command to use") {
                    assertThat(instruction, equalTo("rm -rf /some-directory"))
                }
            }
        }
    }
})
