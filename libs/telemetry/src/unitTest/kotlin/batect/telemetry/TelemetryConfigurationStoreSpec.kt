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

package batect.telemetry

import batect.io.ApplicationPaths
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import batect.testutils.runForEachTest
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files
import java.util.UUID

object TelemetryConfigurationStoreSpec : Spek({
    describe("a telemetry configuration store") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }

        val applicationPaths by createForEachTest {
            mock<ApplicationPaths> {
                on { rootLocalStorageDirectory } doReturn fileSystem.getPath("/home/user/.batect")
            }
        }

        val logger by createLoggerForEachTestWithoutCustomSerializers()

        val telemetryDirectory by createForEachTest { applicationPaths.rootLocalStorageDirectory.resolve("telemetry") }
        val configFilePath by createForEachTest { telemetryDirectory.resolve("config.json") }

        describe("loading the current configuration") {
            fun Suite.itGeneratesAUserIDAndSavesItToDisk() {
                val store by createForEachTest { TelemetryConfigurationStore(applicationPaths, logger) }
                val config by runForEachTest { store.currentConfiguration }

                it("returns that there is no consent information") {
                    assertThat(config.state, equalTo(ConsentState.None))
                }

                it("saves the generated user ID to disk") {
                    assertThat(
                        Files.readAllBytes(configFilePath).toString(Charsets.UTF_8),
                        equivalentTo(
                            """
                                { "state": "none", "userId": "${config.userId}" }
                            """.trimIndent()
                        )
                    )
                }
            }

            given("the telemetry cache directory does not exist") {
                itGeneratesAUserIDAndSavesItToDisk()
            }

            given("the telemetry cache directory does exist") {
                beforeEachTest { Files.createDirectories(telemetryDirectory) }

                given("the configuration file does not exist") {
                    itGeneratesAUserIDAndSavesItToDisk()
                }

                given("the configuration file exists") {
                    val configFileContents = """
                        { "state": "disabled", "userId": "00001111-2222-3333-4444-555566667777" }
                    """.trimIndent()

                    beforeEachTest {
                        Files.write(configFilePath, configFileContents.toByteArray(Charsets.UTF_8))
                    }

                    val store by createForEachTest { TelemetryConfigurationStore(applicationPaths, logger) }

                    it("returns the configuration from the file") {
                        assertThat(store.currentConfiguration, equalTo(TelemetryConfiguration(UUID.fromString("00001111-2222-3333-4444-555566667777"), ConsentState.TelemetryDisabled)))
                    }

                    it("does not modify the configuration on disk") {
                        assertThat(Files.readAllBytes(configFilePath).toString(Charsets.UTF_8), equivalentTo(configFileContents))
                    }
                }
            }
        }

        describe("saving the configuration") {
            fun Suite.itSavesTheConsentStateToDisk() {
                val store by createForEachTest { TelemetryConfigurationStore(applicationPaths, logger) }
                val newState = TelemetryConfiguration(UUID.fromString("00001111-2222-3333-4444-555566667777"), ConsentState.TelemetryAllowed)

                beforeEachTest { store.saveConfiguration(newState) }

                it("saves the configuration to disk") {
                    assertThat(
                        Files.readAllBytes(configFilePath).toString(Charsets.UTF_8),
                        equivalentTo(
                            """
                                { "state": "allowed", "userId": "00001111-2222-3333-4444-555566667777" }
                            """.trimIndent()
                        )
                    )
                }

                it("returns the updated configuration when requested") {
                    assertThat(store.currentConfiguration, equalTo(newState))
                }
            }

            given("the telemetry cache directory does not exist") {
                itSavesTheConsentStateToDisk()
            }

            given("the telemetry cache directory does exist") {
                beforeEachTest { Files.createDirectories(telemetryDirectory) }

                given("there is no existing configuration file") {
                    itSavesTheConsentStateToDisk()
                }

                given("there is an existing configuration file") {
                    beforeEachTest {
                        Files.write(
                            configFilePath,
                            listOf(
                                """
                                    { "state": "disabled", "userId": "aaaabbbb-cccc-dddd-4444-555566667777" }
                                """.trimIndent()
                            )
                        )
                    }

                    itSavesTheConsentStateToDisk()
                }
            }
        }
    }
})
