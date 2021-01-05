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

object TelemetrySpanSpec : Spek({
    describe("a telemetry span") {
        describe("creating a telemetry span") {
            val type = "some-span"
            val validStartTime = ZonedDateTime.now(ZoneOffset.UTC)
            val validEndTime = ZonedDateTime.now(ZoneOffset.UTC)

            given("the span is valid") {
                it("does not throw an exception") {
                    assertThat({ TelemetrySpan(type, validStartTime, validEndTime, emptyMap()) }, doesNotThrow())
                }
            }

            given("the span's start time is not in UTC") {
                it("throws an appropriate exception") {
                    assertThat({ TelemetrySpan(type, validStartTime.withZoneSameInstant(ZoneId.of("Australia/Melbourne")), validEndTime, emptyMap()) }, throws<InvalidTelemetrySpanException>(withMessage("Span start time must be in UTC.")))
                }
            }

            given("the span's end time is not in UTC") {
                it("throws an appropriate exception") {
                    assertThat({ TelemetrySpan(type, validStartTime, validEndTime.withZoneSameInstant(ZoneId.of("Australia/Melbourne")), emptyMap()) }, throws<InvalidTelemetrySpanException>(withMessage("Span end time must be in UTC.")))
                }
            }
        }
    }
})
