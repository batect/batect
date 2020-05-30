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

package batect.execution.model.rules.run

import batect.config.BuildImage
import batect.config.CacheMount
import batect.config.Container
import batect.config.LiteralValue
import batect.config.LocalMount
import batect.config.PullImage
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.execution.ContainerRuntimeConfiguration
import batect.execution.model.events.CachesInitialisedEvent
import batect.execution.model.events.ImageBuiltEvent
import batect.execution.model.events.ImagePulledEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.rules.TaskStepRuleEvaluationResult
import batect.execution.model.steps.CreateContainerStep
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.testutils.osIndependentPath
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Paths

object CreateContainerStepRuleSpec : Spek({
    describe("a create container step rule") {
        val events by createForEachTest { mutableSetOf<TaskEvent>() }
        val config = mock<ContainerRuntimeConfiguration>()

        given("the container uses an existing image") {
            val imageName = "the-image"
            val imageSource = PullImage(imageName)

            given("the container has no cache mounts") {
                val container = Container("the-container", imageSource, volumeMounts = setOf(LocalMount(LiteralValue("/some-local-path"), osIndependentPath("/relative-to"), "/some-container-path")))
                val rule = CreateContainerStepRule(container, config)

                given("the task network has been created") {
                    val network = DockerNetwork("the-network")
                    beforeEachTest { events.add(TaskNetworkCreatedEvent(network)) }

                    given("the image for the container has been pulled") {
                        val image = DockerImage("some-image-id")
                        beforeEachTest { events.add(ImagePulledEvent(imageSource, image)) }

                        on("evaluating the rule") {
                            val result by runForEachTest { rule.evaluate(events) }

                            it("returns a 'create container' step") {
                                assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(CreateContainerStep(container, config, image, network))))
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

            given("the container has a cache mount") {
                val container = Container("the-container", imageSource, volumeMounts = setOf(CacheMount("some-cache", "/some-container-path")))
                val rule = CreateContainerStepRule(container, config)

                given("the task network has been created") {
                    val network = DockerNetwork("the-network")
                    beforeEachTest { events.add(TaskNetworkCreatedEvent(network)) }

                    given("the image for the container has been pulled") {
                        val image = DockerImage("some-image-id")
                        beforeEachTest { events.add(ImagePulledEvent(imageSource, image)) }

                        given("caches have been initialised") {
                            beforeEachTest { events.add(CachesInitialisedEvent) }

                            val result by runForEachTest { rule.evaluate(events) }

                            it("returns a 'create container' step") {
                                assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(CreateContainerStep(container, config, image, network))))
                            }
                        }

                        given("caches have not been initialised") {
                            on("evaluating the rule") {
                                val result by runForEachTest { rule.evaluate(events) }

                                it("indicates that the step is not yet ready") {
                                    assertThat(result, equalTo(TaskStepRuleEvaluationResult.NotReady))
                                }
                            }
                        }
                    }
                }
            }
        }

        given("the container uses an image that must be built") {
            val source = BuildImage(Paths.get("/some-image-directory"))
            val container = Container("the-container", source)
            val rule = CreateContainerStepRule(container, config)

            given("the task network has been created") {
                val network = DockerNetwork("the-network")
                beforeEachTest { events.add(TaskNetworkCreatedEvent(network)) }

                given("the image for the container has been built") {
                    val image = DockerImage("the-built-image")
                    beforeEachTest { events.add(ImageBuiltEvent(container, image)) }

                    on("evaluating the rule") {
                        val result by runForEachTest { rule.evaluate(events) }

                        it("returns a 'create container' step") {
                            assertThat(result, equalTo(TaskStepRuleEvaluationResult.Ready(CreateContainerStep(container, config, image, network))))
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

        describe("toString()") {
            val container = Container("the-container", imageSourceDoesNotMatter())
            val rule = CreateContainerStepRule(container, config)

            it("returns a human-readable representation of itself") {
                assertThat(rule.toString(), equalTo("CreateContainerStepRule(container: 'the-container', config: $config)"))
            }
        }
    }
})
