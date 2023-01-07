/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.execution

import batect.cli.CommandLineOptions
import batect.config.BuildImage
import batect.config.Container
import batect.config.LiteralValue
import batect.config.PullImage
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.throws
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ImageTaggingValidatorSpec : Spek({
    describe("a image tagging validator") {
        val builtImage = BuildImage(LiteralValue("/some/dir"), mock())

        given("the command line options don't include any image tags") {
            val commandLineOptions = CommandLineOptions(imageTags = emptyMap())
            val validator by createForEachTest { ImageTaggingValidator(commandLineOptions) }

            given("no containers are used") {
                it("returns no untagged containers") {
                    assertThat(validator.checkForUntaggedContainers(), isEmpty)
                }
            }

            given("a container is used") {
                beforeEachTest {
                    validator.notifyContainersUsed(setOf(Container("some-container", builtImage)))
                }

                it("returns no untagged containers") {
                    assertThat(validator.checkForUntaggedContainers(), isEmpty)
                }
            }
        }

        given("the command line options include image tags") {
            val commandLineOptions = CommandLineOptions(
                imageTags = mapOf(
                    "some-container" to setOf("some-container:abc123"),
                    "another-container" to setOf("another-container:abc123"),
                ),
            )
            val validator by createForEachTest { ImageTaggingValidator(commandLineOptions) }

            given("no containers are used") {
                it("returns all of the containers as untagged") {
                    assertThat(validator.checkForUntaggedContainers(), equalTo(setOf("some-container", "another-container")))
                }
            }

            given("one of the containers is used") {
                beforeEachTest {
                    validator.notifyContainersUsed(setOf(Container("some-container", builtImage)))
                }

                it("returns the other container as untagged") {
                    assertThat(validator.checkForUntaggedContainers(), equalTo(setOf("another-container")))
                }
            }

            given("one of the containers is used, but that container pulls an image") {
                val container = Container("some-container", PullImage("some-image"))

                it("throws an appropriate exception") {
                    assertThat({ validator.notifyContainersUsed(setOf(container)) }, throws<ContainerUsesPulledImageException>(withMessage("The image built for container 'some-container' was requested to be tagged with --tag-image, but 'some-container' uses a pulled image.")))
                }
            }

            given("both of the containers are used in one task") {
                beforeEachTest {
                    validator.notifyContainersUsed(
                        setOf(
                            Container("some-container", builtImage),
                            Container("another-container", builtImage),
                        ),
                    )
                }

                it("returns no untagged containers") {
                    assertThat(validator.checkForUntaggedContainers(), isEmpty)
                }
            }

            given("both of the containers are used in separate tasks") {
                beforeEachTest {
                    validator.notifyContainersUsed(setOf(Container("some-container", builtImage)))
                    validator.notifyContainersUsed(setOf(Container("another-container", builtImage)))
                }

                it("returns no untagged containers") {
                    assertThat(validator.checkForUntaggedContainers(), isEmpty)
                }
            }

            given("an unrelated container is used") {
                beforeEachTest {
                    validator.notifyContainersUsed(setOf(Container("some-third-container", builtImage)))
                }

                it("returns all of the containers as untagged") {
                    assertThat(validator.checkForUntaggedContainers(), equalTo(setOf("some-container", "another-container")))
                }
            }
        }
    }
})
