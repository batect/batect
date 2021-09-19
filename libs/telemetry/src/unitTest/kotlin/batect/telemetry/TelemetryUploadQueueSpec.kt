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
import batect.testutils.doesNotThrow
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import batect.testutils.runForEachTest
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isEmpty
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.araqnid.hamkrest.json.equivalentTo
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

object TelemetryUploadQueueSpec : Spek({
    describe("a telemetry upload queue") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }

        val applicationPaths by createForEachTest {
            mock<ApplicationPaths> {
                on { rootLocalStorageDirectory } doReturn fileSystem.getPath("/home/user/.batect")
            }
        }

        val logger by createLoggerForEachTestWithoutCustomSerializers()
        val queue by createForEachTest { TelemetryUploadQueue(applicationPaths, logger) }

        val telemetryDirectory by createForEachTest { applicationPaths.rootLocalStorageDirectory.resolve("telemetry") }

        describe("adding a session to the queue") {
            val session = TelemetrySession(
                UUID.fromString("8a1058f8-e41e-4c78-aa42-663b78d15122"),
                UUID.fromString("07ab839b-ac26-475a-966a-77d18d00ac61"),
                ZonedDateTime.of(2020, 8, 7, 3, 49, 10, 678, ZoneOffset.UTC),
                ZonedDateTime.of(2020, 8, 7, 3, 51, 11, 678, ZoneOffset.UTC),
                "my-app",
                "1.0.0",
                mapOf(
                    "someString" to JsonPrimitive("string"),
                    "someNumber" to JsonPrimitive(123),
                    "someBoolean" to JsonPrimitive(false),
                    "someNull" to JsonNull
                ),
                listOf(
                    TelemetryEvent(
                        "some-event",
                        ZonedDateTime.of(2020, 8, 7, 3, 49, 20, 678, ZoneOffset.UTC),
                        mapOf(
                            "someString" to JsonPrimitive("string"),
                            "someNumber" to JsonPrimitive(123),
                            "someBoolean" to JsonPrimitive(false),
                            "someNull" to JsonNull
                        )
                    )
                ),
                listOf(
                    TelemetrySpan(
                        "some-span",
                        ZonedDateTime.of(2020, 8, 7, 3, 49, 30, 678, ZoneOffset.UTC),
                        ZonedDateTime.of(2020, 8, 7, 3, 49, 40, 678, ZoneOffset.UTC),
                        mapOf(
                            "someString" to JsonPrimitive("string"),
                            "someNumber" to JsonPrimitive(123),
                            "someBoolean" to JsonPrimitive(false),
                            "someNull" to JsonNull
                        )
                    )
                )
            )

            fun Suite.itSavesTheSessionToDisk() {
                val expectedPath by createForEachTest { telemetryDirectory.resolve("session-8a1058f8-e41e-4c78-aa42-663b78d15122.json") }
                val actualPath by runForEachTest { queue.add(session) }

                it("saves the session to disk") {
                    assertThat(
                        Files.readAllBytes(expectedPath).toString(Charsets.UTF_8),
                        equivalentTo(
                            """
                                {
                                    "sessionId": "8a1058f8-e41e-4c78-aa42-663b78d15122",
                                    "userId": "07ab839b-ac26-475a-966a-77d18d00ac61",
                                    "sessionStartTime": "2020-08-07T03:49:10.000000678Z",
                                    "sessionEndTime": "2020-08-07T03:51:11.000000678Z",
                                    "applicationId": "my-app",
                                    "applicationVersion": "1.0.0",
                                    "attributes": {
                                        "someString": "string",
                                        "someNumber": 123,
                                        "someBoolean": false,
                                        "someNull": null
                                    },
                                    "events": [
                                        {
                                            "type": "some-event",
                                            "time": "2020-08-07T03:49:20.000000678Z",
                                            "attributes": {
                                                "someString": "string",
                                                "someNumber": 123,
                                                "someBoolean": false,
                                                "someNull": null
                                            }
                                        }
                                    ],
                                    "spans": [
                                        {
                                            "type": "some-span",
                                            "startTime": "2020-08-07T03:49:30.000000678Z",
                                            "endTime": "2020-08-07T03:49:40.000000678Z",
                                            "attributes": {
                                                "someString": "string",
                                                "someNumber": 123,
                                                "someBoolean": false,
                                                "someNull": null
                                            }
                                        }
                                    ]
                                }
                            """.trimIndent()
                        )
                    )
                }

                it("returns the path the session was saved to on disk") {
                    assertThat(actualPath, equalTo(expectedPath))
                }
            }

            given("the queue directory exists") {
                beforeEachTest { Files.createDirectories(telemetryDirectory) }

                itSavesTheSessionToDisk()
            }

            given("the queue directory does not exist") {
                itSavesTheSessionToDisk()
            }
        }

        describe("removing a session from the queue") {
            val sessionPath by createForEachTest { telemetryDirectory.resolve("session-123.json") }

            beforeEachTest { Files.createDirectories(telemetryDirectory) }

            given("the session exists") {
                beforeEachTest {
                    Files.createFile(sessionPath)
                    queue.pop(sessionPath)
                }

                it("removes the file") {
                    assertThat(Files.exists(sessionPath), equalTo(false))
                }
            }

            given("the session does not exist") {
                it("does not throw an exception") {
                    assertThat({ queue.pop(sessionPath) }, doesNotThrow())
                }
            }
        }

        describe("getting all sessions in the queue") {
            given("the queue directory does not exist") {
                it("returns an empty set of sessions") {
                    assertThat(queue.getAll(), isEmpty)
                }
            }

            given("the queue directory exists") {
                beforeEachTest { Files.createDirectories(telemetryDirectory) }

                val telemetryConsentStateFilePath by createForEachTest { telemetryDirectory.resolve("consent.json") }

                given("the queue directory is empty") {
                    it("returns an empty set of sessions") {
                        assertThat(queue.getAll(), isEmpty)
                    }
                }

                given("the queue directory only contains the consent state file") {
                    beforeEachTest { Files.createFile(telemetryConsentStateFilePath) }

                    it("returns an empty set of sessions") {
                        assertThat(queue.getAll(), isEmpty)
                    }
                }

                given("the queue directory only contains a single session") {
                    val sessionPath by createForEachTest { telemetryDirectory.resolve("session-abc123.json") }

                    beforeEachTest { Files.createFile(sessionPath) }

                    it("returns just that session") {
                        assertThat(queue.getAll(), equalTo(setOf(sessionPath)))
                    }
                }

                given("the queue directory contains multiple sessions") {
                    val session1Path by createForEachTest { telemetryDirectory.resolve("session-abc123.json") }
                    val session2Path by createForEachTest { telemetryDirectory.resolve("session-def456.json") }
                    val session3Path by createForEachTest { telemetryDirectory.resolve("session-ghi789.json") }

                    beforeEachTest {
                        Files.createFile(session1Path)
                        Files.createFile(session2Path)
                        Files.createFile(session3Path)
                    }

                    it("returns all of them") {
                        assertThat(queue.getAll(), equalTo(setOf(session1Path, session2Path, session3Path)))
                    }
                }

                given("the queue directory contains multiple sessions and the consent state file") {
                    val session1Path by createForEachTest { telemetryDirectory.resolve("session-abc123.json") }
                    val session2Path by createForEachTest { telemetryDirectory.resolve("session-def456.json") }
                    val session3Path by createForEachTest { telemetryDirectory.resolve("session-ghi789.json") }

                    beforeEachTest {
                        Files.createFile(session1Path)
                        Files.createFile(session2Path)
                        Files.createFile(session3Path)
                        Files.createFile(telemetryConsentStateFilePath)
                    }

                    it("returns all of the sessions, but not the consent state file") {
                        assertThat(queue.getAll(), equalTo(setOf(session1Path, session2Path, session3Path)))
                    }
                }
            }
        }
    }
})
