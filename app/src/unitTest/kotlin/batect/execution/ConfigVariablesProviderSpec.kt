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

package batect.execution

import batect.config.ConfigVariableDefinition
import batect.config.ConfigVariableMap
import batect.config.Configuration
import batect.config.io.ConfigurationException
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.isEmptyMap
import batect.testutils.withFileName
import batect.testutils.withLineNumber
import batect.testutils.withMessage
import com.google.common.jimfs.Configuration as JimfsConfiguration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files
import java.nio.file.Path

object ConfigVariablesProviderSpec : Spek({
    describe("a config variables provider") {
        given("no command line overrides are provided") {
            val commandLineOverrides = emptyMap<String, String>()

            given("no variable file is provided") {
                val sourceFile = null as Path?
                val provider by createForEachTest { ConfigVariablesProvider(commandLineOverrides, sourceFile) }

                given("the configuration has no variables defined") {
                    val config = configWithVariables()

                    beforeEachTest { provider.build(config) }

                    it("returns an empty set of config variables") {
                        assertThat(provider.configVariableValues, isEmptyMap())
                    }
                }

                given("the configuration has a variable with no default defined") {
                    val config = configWithVariables(ConfigVariableDefinition("some_var", "Some description", null))

                    beforeEachTest { provider.build(config) }

                    it("returns that variable with no value") {
                        assertThat(provider.configVariableValues, equalTo(mapOf("some_var" to null)))
                    }
                }

                given("the configuration has a variable with a default defined") {
                    val config = configWithVariables(ConfigVariableDefinition("some_var", "Some description", "the default value"))

                    beforeEachTest { provider.build(config) }

                    it("returns that variable with the default value") {
                        assertThat(provider.configVariableValues, equalTo(mapOf("some_var" to "the default value")))
                    }
                }
            }

            given("a variable file is provided") {
                val fileSystem by createForEachTest { Jimfs.newFileSystem(JimfsConfiguration.unix()) }
                val sourceFile by createForEachTest { fileSystem.getPath("/some-values-file.yml") }
                val provider by createForEachTest { ConfigVariablesProvider(commandLineOverrides, sourceFile) }

                given("the file is well formed") {
                    beforeEachTest { Files.write(sourceFile, listOf("some_var: some value")) }

                    given("the override is for a variable that hasn't been defined") {
                        val config = configWithVariables()

                        it("throws an appropriate exception") {
                            assertThat(
                                { provider.build(config) }, throws<ConfigurationException>(
                                    withMessage("The config variable 'some_var' has not been defined.")
                                        and withLineNumber(1) and withFileName(sourceFile.toString())
                                )
                            )
                        }
                    }

                    given("the override is for a variable that has no default defined") {
                        val config = configWithVariables(ConfigVariableDefinition("some_var", "Some description", null))

                        beforeEachTest { provider.build(config) }

                        it("returns that variable with the provided value") {
                            assertThat(provider.configVariableValues, equalTo(mapOf("some_var" to "some value")))
                        }
                    }

                    given("the override is for a variable with a default defined") {
                        val config = configWithVariables(ConfigVariableDefinition("some_var", "Some description", "the default value"))

                        beforeEachTest { provider.build(config) }

                        it("returns that variable with the provided value") {
                            assertThat(provider.configVariableValues, equalTo(mapOf("some_var" to "some value")))
                        }
                    }
                }

                given("the file is malformed") {
                    val config = configWithVariables()

                    beforeEachTest { Files.write(sourceFile, listOf("blah")) }

                    it("throws an appropriate exception") {
                        assertThat({ provider.build(config) }, throws<ConfigurationException>(
                            withMessage("Expected a map, but got a scalar value")
                                and withLineNumber(1) and withFileName(sourceFile.toString())
                        ))
                    }
                }
            }
        }

        given("command line overrides are provided") {
            val commandLineOverrides = mapOf("some_var" to "some value")

            given("no variable file is provided") {
                val sourceFile = null as Path?
                val provider by createForEachTest { ConfigVariablesProvider(commandLineOverrides, sourceFile) }

                given("the override is for a variable that hasn't been defined") {
                    val config = configWithVariables()

                    it("throws an appropriate exception") {
                        assertThat({ provider.build(config) }, throws<ConfigurationException>(withMessage("The config variable 'some_var' set with --config-var has not been defined.")))
                    }
                }

                given("the override is for a variable that has no default defined") {
                    val config = configWithVariables(ConfigVariableDefinition("some_var", "Some description", null))

                    beforeEachTest { provider.build(config) }

                    it("returns that variable with the provided value") {
                        assertThat(provider.configVariableValues, equalTo(mapOf("some_var" to "some value")))
                    }
                }

                given("the override is for a variable with a default defined") {
                    val config = configWithVariables(ConfigVariableDefinition("some_var", "Some description", "the default value"))

                    beforeEachTest { provider.build(config) }

                    it("returns that variable with the provided value") {
                        assertThat(provider.configVariableValues, equalTo(mapOf("some_var" to "some value")))
                    }
                }
            }

            given("a variable file is provided") {
                val fileSystem by createForEachTest { Jimfs.newFileSystem(JimfsConfiguration.unix()) }
                val sourceFile by createForEachTest { fileSystem.getPath("/some-values-file.yml") }
                val provider by createForEachTest { ConfigVariablesProvider(commandLineOverrides, sourceFile) }

                given("the file also contains a value for the same variable") {
                    val config = configWithVariables(ConfigVariableDefinition("some_var", "Some description", "the default value"))

                    beforeEachTest {
                        Files.write(sourceFile, listOf("some_var: some file value"))
                        provider.build(config)
                    }

                    it("returns the variable with the value from the command line") {
                        assertThat(provider.configVariableValues, equalTo(mapOf("some_var" to "some value")))
                    }
                }
            }
        }
    }
})

private fun configWithVariables(vararg variables: ConfigVariableDefinition): Configuration =
    Configuration("the_project", configVariables = ConfigVariableMap(variables.asIterable()))
