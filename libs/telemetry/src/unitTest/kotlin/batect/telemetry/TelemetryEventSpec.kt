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

import batect.testutils.doesNotThrow
import batect.testutils.given
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

object TelemetryEventSpec : Spek({
    describe("a telemetry event") {
        describe("creating a telemetry event") {
            val type = "some-event"
            val validTime = ZonedDateTime.now(ZoneOffset.UTC)

            given("the event is valid") {
                it("does not throw an exception") {
                    assertThat({ TelemetryEvent(type, validTime, emptyMap()) }, doesNotThrow())
                }
            }

            given("the event's time is not in UTC") {
                it("throws an appropriate exception") {
                    assertThat({ TelemetryEvent(type, validTime.withZoneSameInstant(ZoneId.of("Australia/Melbourne")), emptyMap()) }, throws<InvalidTelemetryEventException>(withMessage("Event time must be in UTC.")))
                }
            }
        }
    }
})
