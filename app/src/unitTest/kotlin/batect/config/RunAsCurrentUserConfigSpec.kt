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

package batect.config

import batect.testutils.on
import batect.testutils.withColumn
import batect.testutils.withLineNumber
import batect.testutils.withMessage
import com.charleskorn.kaml.Yaml
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RunAsCurrentUserConfigSpec : Spek({
    describe("'run as current user' configuration") {
        describe("deserializing from YAML") {
            on("parsing a valid configuration that enables 'run as current user'") {
                val config = fromYaml(
                    """
                        enabled: true
                        home_directory: /home/the_user
                    """.trimIndent()
                )

                it("returns a configuration that runs as the current user") {
                    assertThat(config, isA<RunAsCurrentUserConfig.RunAsCurrentUser>())
                }

                it("returns the correct home directory") {
                    assertThat((config as RunAsCurrentUserConfig.RunAsCurrentUser).homeDirectory, equalTo("/home/the_user"))
                }
            }

            on("parsing a valid configuration that disables 'run as current user'") {
                val config = fromYaml(
                    """
                        enabled: false
                    """.trimIndent()
                )

                it("returns a configuration that runs as the default container user") {
                    assertThat(config, isA<RunAsCurrentUserConfig.RunAsDefaultContainerUser>())
                }
            }

            on("parsing a configuration that enables 'run as current user' but does not provide a home directory") {
                val yaml = """
                    enabled: true
                """.trimIndent()

                it("throws an appropriate exception") {
                    assertThat({ fromYaml(yaml) }, throws(withMessage("Running as the current user has been enabled, but a home directory for that user has not been provided.") and withLineNumber(1) and withColumn(1)))
                }
            }

            on("parsing a configuration that disables 'run as current user' but also provides a home directory") {
                val yaml = """
                    enabled: false
                    home_directory: /home/the_user
                """.trimIndent()

                it("throws an appropriate exception") {
                    assertThat({ fromYaml(yaml) }, throws(withMessage("Running as the current user has not been enabled, but a home directory for that user has been provided.") and withLineNumber(1) and withColumn(1)))
                }
            }

            on("parsing a configuration that does not enable 'run as current user' but provides a home directory") {
                val yaml = """
                    home_directory: /home/the_user
                """.trimIndent()

                it("throws an appropriate exception") {
                    assertThat({ fromYaml(yaml) }, throws(withMessage("Running as the current user has not been enabled, but a home directory for that user has been provided.") and withLineNumber(1) and withColumn(1)))
                }
            }
        }
    }
})

private fun fromYaml(yaml: String) = Yaml.default.decodeFromString(RunAsCurrentUserConfig.serializer(), yaml)
