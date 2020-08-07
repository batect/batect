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

package batect.telemetry

import batect.io.ApplicationPaths
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import batect.testutils.withMessage
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import java.nio.file.Files
import java.util.UUID
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe

object ConsentStateStoreSpec : Spek({
    describe("a consent state store") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }

        val applicationPaths by createForEachTest {
            mock<ApplicationPaths> {
                on { rootLocalStorageDirectory } doReturn fileSystem.getPath("/home/user/.batect")
            }
        }

        val logger by createLoggerForEachTestWithoutCustomSerializers()

        val telemetryDirectory by createForEachTest { applicationPaths.rootLocalStorageDirectory.resolve("telemetry") }
        val telemetryConsentStateFilePath by createForEachTest { telemetryDirectory.resolve("consent.json") }

        describe("loading the current consent state") {
            given("the telemetry cache directory does not exist") {
                val store by createForEachTest { ConsentStateStore(applicationPaths, logger) }

                it("returns that there is no consent information") {
                    assertThat(store.consentState, equalTo(ConsentState.None))
                }
            }

            given("the telemetry cache directory does exist") {
                beforeEachTest { Files.createDirectories(telemetryDirectory) }

                given("the consent state file does not exist") {
                    val store by createForEachTest { ConsentStateStore(applicationPaths, logger) }

                    it("returns that there is no consent information") {
                        assertThat(store.consentState, equalTo(ConsentState.None))
                    }
                }

                given("the consent state file exists") {
                    beforeEachTest {
                        Files.write(telemetryConsentStateFilePath, listOf("""
                            { "type": "disabled" }
                        """.trimIndent()))
                    }

                    val store by createForEachTest { ConsentStateStore(applicationPaths, logger) }

                    it("returns the consent state from the file") {
                        assertThat(store.consentState, equalTo(ConsentState.Disabled))
                    }
                }
            }
        }

        describe("saving the consent state") {
            fun Suite.itSavesTheConsentStateToDisk() {
                val store by createForEachTest { ConsentStateStore(applicationPaths, logger) }
                val newState = ConsentState.Enabled(UUID.fromString("00001111-2222-3333-4444-555566667777"))

                beforeEachTest { store.saveConsentState(newState) }

                it("saves the consent state to disk") {
                    assertThat(Files.readAllBytes(telemetryConsentStateFilePath).toString(Charsets.UTF_8), equivalentTo("""
                        { "type": "enabled", "userId": "00001111-2222-3333-4444-555566667777" }
                    """.trimIndent()))
                }

                it("returns the updated consent state when requested") {
                    assertThat(store.consentState, equalTo(newState))
                }
            }

            given("the telemetry cache directory does not exist") {
                itSavesTheConsentStateToDisk()
            }

            given("the telemetry cache directory does exist") {
                beforeEachTest { Files.createDirectories(telemetryDirectory) }

                given("there is no existing consent state file") {
                    itSavesTheConsentStateToDisk()
                }

                given("there is an existing consent state file") {
                    beforeEachTest {
                        Files.write(telemetryConsentStateFilePath, listOf("""
                            { "type": "disabled" }
                        """.trimIndent()))
                    }

                    given("the consent state is not 'none'") {
                        itSavesTheConsentStateToDisk()
                    }

                    given("an attempt is made to store the 'none' consent state") {
                        val store by createForEachTest { ConsentStateStore(applicationPaths, logger) }

                        it("throws an appropriate exception") {
                            assertThat({ store.saveConsentState(ConsentState.None) }, throws(withMessage("Cannot save the 'none' consent state.")))
                        }
                    }
                }
            }
        }
    }
})
