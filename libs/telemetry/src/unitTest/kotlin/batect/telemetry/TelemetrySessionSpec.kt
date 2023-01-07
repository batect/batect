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

package batect.telemetry

import batect.testutils.doesNotThrow
import batect.testutils.given
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

object TelemetrySessionSpec : Spek({
    describe("a telemetry session") {
        describe("creating a telemetry session") {
            val validSessionId = UUID.randomUUID()
            val validUserId = UUID.randomUUID()
            val validSessionStartTime = ZonedDateTime.now(ZoneOffset.UTC)
            val validSessionEndTime = ZonedDateTime.now(ZoneOffset.UTC)
            val appName = "my-app"
            val appVersion = "1.0.0"
            val nonV4UUID = UUID.nameUUIDFromBytes(byteArrayOf(0x01))

            given("the session is valid") {
                it("does not throw an exception") {
                    assertThat({ TelemetrySession(validSessionId, validUserId, validSessionStartTime, validSessionEndTime, appName, appVersion, emptyMap(), emptyList(), emptyList()) }, doesNotThrow())
                }
            }

            given("the session ID is not a v4 (random) UUID") {
                it("throws an appropriate exception") {
                    assertThat({ TelemetrySession(nonV4UUID, validUserId, validSessionStartTime, validSessionEndTime, appName, appVersion, emptyMap(), emptyList(), emptyList()) }, throws<InvalidTelemetrySessionException>(withMessage("Session ID must be a v4 (random) UUID.")))
                }
            }

            given("the user ID is not a v4 (random) UUID") {
                it("throws an appropriate exception") {
                    assertThat({ TelemetrySession(validSessionId, nonV4UUID, validSessionStartTime, validSessionEndTime, appName, appVersion, emptyMap(), emptyList(), emptyList()) }, throws<InvalidTelemetrySessionException>(withMessage("User ID must be a v4 (random) UUID.")))
                }
            }

            given("the start time is not in UTC") {
                it("throws an appropriate exception") {
                    assertThat({ TelemetrySession(validSessionId, validUserId, validSessionStartTime.withZoneSameInstant(ZoneId.of("Australia/Melbourne")), validSessionEndTime, appName, appVersion, emptyMap(), emptyList(), emptyList()) }, throws<InvalidTelemetrySessionException>(withMessage("Session start time must be in UTC.")))
                }
            }

            given("the end time is not in UTC") {
                it("throws an appropriate exception") {
                    assertThat({ TelemetrySession(validSessionId, validUserId, validSessionStartTime, validSessionEndTime.withZoneSameInstant(ZoneId.of("Australia/Melbourne")), appName, appVersion, emptyMap(), emptyList(), emptyList()) }, throws<InvalidTelemetrySessionException>(withMessage("Session end time must be in UTC.")))
                }
            }
        }

        describe("serializing a telemetry session to JSON") {
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
                    "someNull" to JsonNull,
                ),
                listOf(
                    TelemetryEvent(
                        "some-event",
                        ZonedDateTime.of(2020, 8, 7, 3, 49, 20, 678, ZoneOffset.UTC),
                        mapOf(
                            "someString" to JsonPrimitive("string"),
                            "someNumber" to JsonPrimitive(123),
                            "someBoolean" to JsonPrimitive(false),
                            "someNull" to JsonNull,
                        ),
                    ),
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
                            "someNull" to JsonNull,
                        ),
                    ),
                ),
            )

            it("serializes to the expected JSON") {
                assertThat(
                    Json.Default.encodeToString(TelemetrySession.serializer(), session),
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
                        """.trimIndent(),
                    ),
                )
            }
        }
    }
})
