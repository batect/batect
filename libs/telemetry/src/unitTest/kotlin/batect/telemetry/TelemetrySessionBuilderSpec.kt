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

import batect.primitives.ApplicationVersionInfoProvider
import batect.primitives.Version
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonLiteral
import kotlinx.serialization.json.JsonNull
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

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
        var returnStartTime = true

        val timeSource: TimeSource = { if (returnStartTime) startTime else endTime }
        beforeEachTest { returnStartTime = true }

        describe("building a session with no attributes") {
            val builder by createForEachTest { TelemetrySessionBuilder(versionInfo, timeSource) }

            val session by runForEachTest {
                returnStartTime = false
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
            val session by createForEachTest {
                TelemetrySessionBuilder(versionInfo, timeSource)
                    .addAttribute("thingType", "stuff")
                    .build(telemetryConfigurationStore)
            }

            it("stores the attribute on the built session") {
                assertThat(session.attributes, equalTo(mapOf("thingType" to JsonLiteral("stuff"))))
            }
        }

        describe("building a session with a null string attribute") {
            val session by createForEachTest {
                TelemetrySessionBuilder(versionInfo, timeSource)
                    .addAttribute("thingType", null as String?)
                    .build(telemetryConfigurationStore)
            }

            it("stores the attribute on the built session") {
                assertThat(session.attributes, equalTo(mapOf("thingType" to JsonNull)))
            }
        }

        describe("building a session with an integer attribute") {
            val session by createForEachTest {
                TelemetrySessionBuilder(versionInfo, timeSource)
                    .addAttribute("thingCount", 12)
                    .build(telemetryConfigurationStore)
            }

            it("stores the attribute on the built session") {
                assertThat(session.attributes, equalTo(mapOf("thingCount" to JsonLiteral(12))))
            }
        }

        describe("building a session with a null integer attribute") {
            val session by createForEachTest {
                TelemetrySessionBuilder(versionInfo, timeSource)
                    .addAttribute("thingCount", null as Int?)
                    .build(telemetryConfigurationStore)
            }

            it("stores the attribute on the built session") {
                assertThat(session.attributes, equalTo(mapOf("thingCount" to JsonNull)))
            }
        }

        describe("building a session with a boolean attribute") {
            val session by createForEachTest {
                TelemetrySessionBuilder(versionInfo, timeSource)
                    .addAttribute("thingEnabled", false)
                    .build(telemetryConfigurationStore)
            }

            it("stores the attribute on the built session") {
                assertThat(session.attributes, equalTo(mapOf("thingEnabled" to JsonLiteral(false))))
            }
        }

        describe("building a session with a null boolean attribute") {
            val session by createForEachTest {
                TelemetrySessionBuilder(versionInfo, timeSource)
                    .addAttribute("thingEnabled", null as Boolean?)
                    .build(telemetryConfigurationStore)
            }

            it("stores the attribute on the built session") {
                assertThat(session.attributes, equalTo(mapOf("thingEnabled" to JsonNull)))
            }
        }

        describe("building a session with a null attribute") {
            val session by createForEachTest {
                TelemetrySessionBuilder(versionInfo, timeSource)
                    .addNullAttribute("thing")
                    .build(telemetryConfigurationStore)
            }

            it("stores the attribute on the built session") {
                assertThat(session.attributes, equalTo(mapOf("thing" to JsonNull)))
            }
        }

        describe("building a session with multiple attributes") {
            val session by createForEachTest {
                TelemetrySessionBuilder(versionInfo, timeSource)
                    .addAttribute("thingType", "stuff")
                    .addAttribute("thingCount", 12)
                    .addAttribute("thingEnabled", false)
                    .addNullAttribute("thing")
                    .build(telemetryConfigurationStore)
            }

            it("stores all of the attributes on the built session") {
                assertThat(session.attributes, equalTo(mapOf(
                    "thingType" to JsonLiteral("stuff"),
                    "thingCount" to JsonLiteral(12),
                    "thingEnabled" to JsonLiteral(false),
                    "thing" to JsonNull
                )))
            }
        }

        describe("building a session with two attributes of the same name") {
            given("the existing attribute is a string") {
                val builder by createForEachTest {
                    TelemetrySessionBuilder(versionInfo, timeSource)
                        .addAttribute("thing", "stuff")
                }

                it("does not allow adding a string attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", "other stuff") }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding an integer attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", 123) }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding a boolean attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", false) }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding a null attribute of the same name") {
                    assertThat({ builder.addNullAttribute("thing") }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }
            }

            given("the existing attribute is an integer") {
                val builder by createForEachTest {
                    TelemetrySessionBuilder(versionInfo, timeSource)
                        .addAttribute("thing", 123)
                }

                it("does not allow adding a string attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", "other stuff") }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding an integer attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", 123) }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding a boolean attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", false) }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding a null attribute of the same name") {
                    assertThat({ builder.addNullAttribute("thing") }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }
            }

            given("the existing attribute is a boolean") {
                val builder by createForEachTest {
                    TelemetrySessionBuilder(versionInfo, timeSource)
                        .addAttribute("thing", false)
                }

                it("does not allow adding a string attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", "other stuff") }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding an integer attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", 123) }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding a boolean attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", false) }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding a null attribute of the same name") {
                    assertThat({ builder.addNullAttribute("thing") }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }
            }

            given("the existing attribute is null") {
                val builder by createForEachTest {
                    TelemetrySessionBuilder(versionInfo, timeSource)
                        .addNullAttribute("thing")
                }

                it("does not allow adding a string attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", "other stuff") }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding an integer attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", 123) }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding a boolean attribute of the same name") {
                    assertThat({ builder.addAttribute("thing", false) }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }

                it("does not allow adding a null attribute of the same name") {
                    assertThat({ builder.addNullAttribute("thing") }, throws<IllegalArgumentException>(withMessage("Attribute 'thing' already added.")))
                }
            }
        }
    }
})
