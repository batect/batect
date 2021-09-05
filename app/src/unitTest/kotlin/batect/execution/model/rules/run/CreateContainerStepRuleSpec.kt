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

import batect.config.BuildImage
import batect.config.Container
import batect.config.LiteralValue
import batect.config.PullImage
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.execution.model.events.ImageBuiltEvent
import batect.execution.model.events.ImagePulledEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskNetworkReadyEvent
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.CreateContainerStep
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.logRepresentationOf
import batect.testutils.on
import batect.testutils.pathResolutionContextDoesNotMatter
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import org.araqnid.hamkrest.json.equivalentTo
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object CreateContainerStepRuleSpec : Spek({
    describe("a create container step rule") {
        val events by createForEachTest { mutableSetOf<TaskEvent>() }

        given("the container uses an existing image") {
            val imageSource = PullImage("the-image")
            val container = Container("the-container", imageSource)
            val rule = CreateContainerStepRule(container)

            given("the task network is ready") {
                val dockerNetwork = DockerNetwork("the-network")
                val networkReadyEvent = mock<TaskNetworkReadyEvent> {
                    on { network } doReturn dockerNetwork
                }

                beforeEachTest { events.add(networkReadyEvent) }

                given("the image for the container has been pulled") {
                    val image = DockerImage("some-image-id")
                    beforeEachTest { events.add(ImagePulledEvent(imageSource, image)) }

                    on("evaluating the rule") {
                        val result by runForEachTest { rule.evaluate(events) }

                        it("returns a 'create container' step") {
                            assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(CreateContainerStep(container, image, dockerNetwork))))
                        }
                    }
                }

                given("an image has been pulled for another container") {
                    beforeEachTest { events.add(ImagePulledEvent(PullImage("some-other-image"), DockerImage("some-other-image-id"))) }

                    on("evaluating the rule") {
                        val result by runForEachTest { rule.evaluate(events) }

                        it("indicates that the step is not yet ready") {
                            assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                        }
                    }
                }

                given("no images have been pulled") {
                    on("evaluating the rule") {
                        val result by runForEachTest { rule.evaluate(events) }

                        it("indicates that the step is not yet ready") {
                            assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                        }
                    }
                }
            }

            given("the task network has not been created") {
                on("evaluating the rule") {
                    val result by runForEachTest { rule.evaluate(events) }

                    it("indicates that the step is not yet ready") {
                        assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                    }
                }
            }
        }

        given("the container uses an image that must be built") {
            val source = BuildImage(LiteralValue("/some-image-directory"), pathResolutionContextDoesNotMatter())
            val container = Container("the-container", source)
            val rule = CreateContainerStepRule(container)

            given("the task network is ready") {
                val dockerNetwork = DockerNetwork("the-network")
                val networkReadyEvent = mock<TaskNetworkReadyEvent> {
                    on { network } doReturn dockerNetwork
                }

                beforeEachTest { events.add(networkReadyEvent) }

                given("the image for the container has been built") {
                    val image = DockerImage("the-built-image")
                    beforeEachTest { events.add(ImageBuiltEvent(container, image)) }

                    on("evaluating the rule") {
                        val result by runForEachTest { rule.evaluate(events) }

                        it("returns a 'create container' step") {
                            assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(CreateContainerStep(container, image, dockerNetwork))))
                        }
                    }
                }

                given("an image has been built for another container") {
                    val otherContainer = Container("the-other-container", imageSourceDoesNotMatter())
                    beforeEachTest { events.add(ImageBuiltEvent(otherContainer, DockerImage("some-other-image"))) }

                    on("evaluating the rule") {
                        val result by runForEachTest { rule.evaluate(events) }

                        it("indicates that the step is not yet ready") {
                            assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                        }
                    }
                }

                given("no images have been built") {
                    on("evaluating the rule") {
                        val result by runForEachTest { rule.evaluate(events) }

                        it("indicates that the step is not yet ready") {
                            assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                        }
                    }
                }
            }

            given("the task network has not been created") {
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
            val rule = CreateContainerStepRule(container)

            it("returns a machine-readable representation of itself") {
                assertThat(
                    logRepresentationOf(rule),
                    equivalentTo(
                        """
                        |{
                        |   "type": "${rule::class.qualifiedName}",
                        |   "container": "the-container"
                        |}
                        """.trimMargin()
                    )
                )
            }
        }
    }
})
