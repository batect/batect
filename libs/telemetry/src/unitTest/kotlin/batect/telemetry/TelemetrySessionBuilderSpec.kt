/*
    Copyright 2017-2022 Charles Korn.

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

import batect.primitives.ApplicationVersionInfoProvider
import batect.primitives.Version
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

object TelemetrySessionBuilderSpec : Spek({
    describe("a telemetry session builder") {
        val userId = UUID.fromString("11112222-3333-4444-5555-666677778888")
        val telemetryConfigurationStore by createForEachTest {
            mock<TelemetryConfigurationStore> {
                on { currentConfiguration } doReturn TelemetryConfiguration(userId, ConsentState.None)
            }
        }

        val versionInfo by createForEachTest {
            mock<ApplicationVersionInfoProvider> {
                on { version } doReturn Version(1, 2, 3)
            }
        }

        val startTime = ZonedDateTime.of(2020, 8, 11, 1, 3, 0, 0, ZoneOffset.UTC)
        val endTime = ZonedDateTime.of(2020, 8, 11, 1, 3, 30, 123456789, ZoneOffset.UTC)
        var timeNow = startTime
        val timeSource: TimeSource = { timeNow }
        beforeEachTest { timeNow = startTime }

        describe("building a session with no attributes") {
            val builder by createForEachTest { TelemetrySessionBuilder(versionInfo, timeSource) }

            val session by runForEachTest {
                timeNow = endTime
                builder.build(telemetryConfigurationStore)
            }

            it("sets the session ID to a v4 random UUID") {
                assertThat(session.sessionId.version(), equalTo(4))
            }

            it("sets the user ID to the user ID from the telemetry configuration") {
                assertThat(session.userId, equalTo(userId))
            }

            it("captures the start time when the session is created") {
                assertThat(session.sessionStartTime, equalTo(startTime))
            }

            it("captures the end time when the session is built") {
                assertThat(session.sessionEndTime, equalTo(endTime))
            }

            it("sets the application ID") {
                assertThat(session.applicationId, equalTo("batect"))
            }

            it("sets the application version to the current version") {
                assertThat(session.applicationVersion, equalTo("1.2.3"))
            }
        }

        describe("building a session with a string attribute") {
            val builder by createForEachTest { TelemetrySessionBuilder(versionInfo, timeSource) }

            val session by createForEachTest {
                builder.addAttribute("thingType", "stuff")
                builder.build(telemetryConfigurationStore)
            }

            it("stores the attribute on the built session") {
                assertThat(session.attributes, equalTo(mapOf("thingType" to JsonPrimitive("stuff"))))
            }
        }

        describe("building a session with a null string attribute") {
            val builder by createForEachTest { TelemetrySessionBuilder(versionInfo, timeSource) }

            val session by createForEachTest {
                builder.addAttribute("thingType", null as String?)
                builder.build(telemetryConfigurationStore)
            }

            it("stores the attribute on the built session") {
                assertThat(session.attributes, equalTo(mapOf("thingType" to JsonNull)))
            }
        }

        describe("building a session with an integer attribute") {
            val builder by createForEachTest { TelemetrySessionBuilder(versionInfo, timeSource) }

            val session by createForEachTest {
                builder.addAttribute("thingCount", 12)
                builder.build(telemetryConfigurationStore)
            }

            it("stores the attribute on the built session") {
                assertThat(session.attributes, equalTo(mapOf("thingCount" to JsonPrimitive(12))))
            }
        }

        describe("building a session with a null integer attribute") {
            val builder by createForEachTest { TelemetrySessionBuilder(versionInfo, timeSource) }

            val session by createForEachTest {
                builder.addAttribute("thingCount", null as Int?)
                builder.build(telemetryConfigurationStore)
            }

            it("stores the attribute on the built session") {
                assertThat(session.attributes, equalTo(mapOf("thingCount" to JsonNull)))
            }
        }

        describe("building a session with a boolean attribute") {
            val builder by createForEachTest { TelemetrySessionBuilder(versionInfo, timeSource) }

            val session by createForEachTest {
                builder.addAttribute("thingEnabled", false)
                builder.build(telemetryConfigurationStore)
            }

            it("stores the attribute on the built session") {
                assertThat(session.attributes, equalTo(mapOf("thingEnabled" to JsonPrimitive(false))))
            }
        }

        describe("building a session with a null boolean attribute") {
            val builder by createForEachTest { TelemetrySessionBuilder(versionInfo, timeSource) }

            val session by createForEachTest {
                builder.addAttribute("thingEnabled", null as Boolean?)
                builder.build(telemetryConfigurationStore)
            }

            it("stores the attribute on the built session") {
                assertThat(session.attributes, equalTo(mapOf("thingEnabled" to JsonNull)))
            }
        }

        describe("building a session with a null attribute") {
            val builder by createForEachTest { TelemetrySessionBuilder(versionInfo, timeSource) }

            val session by createForEachTest {
                builder.addNullAttribute("thing")
                builder.build(telemetryConfigurationStore)
            }

            it("stores the attribute on the built session") {
                assertThat(session.attributes, equalTo(mapOf("thing" to JsonNull)))
            }
        }

        describe("building a session with multiple attributes") {
            val builder by createForEachTest { TelemetrySessionBuilder(versionInfo, timeSource) }

            val session by createForEachTest {
                builder.addAttribute("thingType", "stuff")
                builder.addAttribute("thingCount", 12)
                builder.addAttribute("thingEnabled", false)
                builder.addNullAttribute("thing")
                builder.build(telemetryConfigurationStore)
            }

            it("stores all of the attributes on the built session") {
                assertThat(
                    session.attributes,
                    equalTo(
                        mapOf(
                            "thingType" to JsonPrimitive("stuff"),
                            "thingCount" to JsonPrimitive(12),
                            "thingEnabled" to JsonPrimitive(false),
                            "thing" to JsonNull
                        )
                    )
                )
            }
        }

        describe("creating a session with an event") {
            val builder by createForEachTest { TelemetrySessionBuilder(versionInfo, timeSource) }
            val eventTime = ZonedDateTime.of(2020, 8, 11, 1, 3, 10, 0, ZoneOffset.UTC)

            val session by createForEachTest {
                timeNow = eventTime

                builder.addEvent(
                    "MyEvent",
                    mapOf(
                        "thingType" to AttributeValue("stuff"),
                        "thingCount" to AttributeValue(12),
                        "thingEnabled" to AttributeValue(false)
                    )
                )

                builder.build(telemetryConfigurationStore)
            }

            it("stores the event with the provided attributes and correct time") {
                assertThat(
                    session.events,
                    equalTo(
                        listOf(
                            TelemetryEvent(
                                "MyEvent",
                                eventTime,
                                mapOf(
                                    "thingType" to JsonPrimitive("stuff"),
                                    "thingCount" to JsonPrimitive(12),
                                    "thingEnabled" to JsonPrimitive(false)
                                )
                            )
                        )
                    )
                )
            }
        }

        describe("creating a session with a span") {
            val builder by createForEachTest { TelemetrySessionBuilder(versionInfo, timeSource) }
            val spanStartTime = ZonedDateTime.of(2020, 8, 11, 1, 3, 10, 0, ZoneOffset.UTC)
            val spanEndTime = ZonedDateTime.of(2020, 8, 11, 1, 3, 20, 0, ZoneOffset.UTC)
            val expectedSpan = TelemetrySpan(
                "MySpan",
                spanStartTime,
                spanEndTime,
                mapOf(
                    "thingType" to JsonPrimitive("stuff"),
                    "thingCount" to JsonPrimitive(12),
                    "thingEnabled" to JsonPrimitive(false)
                )
            )

            given("the span function does not throw") {
                val session by createForEachTest {
                    timeNow = spanStartTime

                    builder.addSpan("MySpan") { span ->
                        span.addAttribute("thingType", "stuff")
                        span.addAttribute("thingCount", 12)
                        span.addAttribute("thingEnabled", false)

                        timeNow = spanEndTime
                    }

                    builder.build(telemetryConfigurationStore)
                }

                it("stores the span with the provided attributes and correct times") {
                    assertThat(session.spans, equalTo(listOf(expectedSpan)))
                }
            }

            given("the span function throws") {
                val session by createForEachTest {
                    timeNow = spanStartTime

                    val exceptionToThrow = RuntimeException("Something went wrong.")

                    try {
                        builder.addSpan("MySpan") { span ->
                            span.addAttribute("thingType", "stuff")
                            span.addAttribute("thingCount", 12)
                            span.addAttribute("thingEnabled", false)

                            timeNow = spanEndTime

                            throw exceptionToThrow
                        }

                        // Should never get to here.
                    } catch (e: RuntimeException) {
                        if (e != exceptionToThrow) {
                            throw e
                        }
                    }

                    builder.build(telemetryConfigurationStore)
                }

                it("still stores the span with the provided attributes and correct times") {
                    assertThat(session.spans, equalTo(listOf(expectedSpan)))
                }
            }
        }
    }
})
