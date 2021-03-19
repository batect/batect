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

package batect.config

import batect.cli.CommandLineOptions
import batect.config.io.ConfigurationException
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import batect.testutils.pathResolutionContextDoesNotMatter
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TaskSpecialisedConfigurationFactorySpec : Spek({
    describe("overriding image sources") {
        val container1 = Container("container-1", BuildImage(LiteralValue("some-build-dir"), pathResolutionContextDoesNotMatter()))
        val container2 = Container("container-2", PullImage("some-image"))
        val rawConfig = createRawConfiguration(container1, container2)

        val logger by createLoggerForEachTestWithoutCustomSerializers()

        given("no overrides") {
            val commandLineOptions = CommandLineOptions(imageOverrides = emptyMap())
            val factory by createForEachTest { TaskSpecialisedConfigurationFactory(rawConfig, commandLineOptions, logger) }

            it("returns the original configuration unmodified") {
                assertThat(factory.create(), equalTo(createTaskSpecialisedConfiguration(container1, container2)))
            }
        }

        given("a single override") {
            val commandLineOptions = CommandLineOptions(
                imageOverrides = mapOf(
                    container1.name to "another-image"
                )
            )

            val factory by createForEachTest { TaskSpecialisedConfigurationFactory(rawConfig, commandLineOptions, logger) }

            it("returns a new configuration with the image for the given container overridden") {
                assertThat(factory.create(), equalTo(createTaskSpecialisedConfiguration(Container("container-1", PullImage("another-image")), container2)))
            }
        }

        given("multiple overrides") {
            val commandLineOptions = CommandLineOptions(
                imageOverrides = mapOf(
                    container1.name to "another-image",
                    container2.name to "another-other-image",
                )
            )

            val factory by createForEachTest { TaskSpecialisedConfigurationFactory(rawConfig, commandLineOptions, logger) }

            it("returns a new configuration with the images for the given containers overridden") {
                assertThat(factory.create(), equalTo(createTaskSpecialisedConfiguration(Container("container-1", PullImage("another-image")), Container("container-2", PullImage("another-other-image")))))
            }
        }

        given("an override for a container that doesn't exist") {
            val commandLineOptions = CommandLineOptions(
                imageOverrides = mapOf(
                    "another-container" to "another-image"
                )
            )

            val factory by createForEachTest { TaskSpecialisedConfigurationFactory(rawConfig, commandLineOptions, logger) }

            it("throws an appropriate exception") {
                assertThat({ factory.create() }, throws<ConfigurationException>(withMessage("Cannot override image for container 'another-container' because there is no container named 'another-container' defined.")))
            }
        }
    }
})

private fun createRawConfiguration(vararg containers: Container): RawConfiguration = RawConfiguration("my_project", TaskMap(), ContainerMap(*containers))
private fun createTaskSpecialisedConfiguration(vararg containers: Container): TaskSpecialisedConfiguration = TaskSpecialisedConfiguration("my_project", TaskMap(), ContainerMap(*containers))
