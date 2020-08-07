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

import batect.testutils.doesNotThrow
import batect.testutils.given
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

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
                    assertThat({ TelemetrySession(validSessionId, validUserId, validSessionStartTime, validSessionEndTime, appName, appVersion) }, doesNotThrow())
                }
            }

            given("the session ID is not a v4 (random) UUID") {
                it("throws an appropriate exception") {
                    assertThat({ TelemetrySession(nonV4UUID, validUserId, validSessionStartTime, validSessionEndTime, appName, appVersion) }, throws<InvalidTelemetrySessionException>(withMessage("Session ID must be a v4 (random) UUID.")))
                }
            }

            given("the user ID is not a v4 (random) UUID") {
                it("throws an appropriate exception") {
                    assertThat({ TelemetrySession(validSessionId, nonV4UUID, validSessionStartTime, validSessionEndTime, appName, appVersion) }, throws<InvalidTelemetrySessionException>(withMessage("User ID must be a v4 (random) UUID.")))
                }
            }

            given("the start time is not in UTC") {
                it("throws an appropriate exception") {
                    assertThat({ TelemetrySession(validSessionId, validUserId, validSessionStartTime.withZoneSameInstant(ZoneId.of("Australia/Melbourne")), validSessionEndTime, appName, appVersion) }, throws<InvalidTelemetrySessionException>(withMessage("Session start time must be in UTC.")))
                }
            }

            given("the end time is not in UTC") {
                it("throws an appropriate exception") {
                    assertThat({ TelemetrySession(validSessionId, validUserId, validSessionStartTime, validSessionEndTime.withZoneSameInstant(ZoneId.of("Australia/Melbourne")), appName, appVersion) }, throws<InvalidTelemetrySessionException>(withMessage("Session end time must be in UTC.")))
                }
            }
        }
    }
})
