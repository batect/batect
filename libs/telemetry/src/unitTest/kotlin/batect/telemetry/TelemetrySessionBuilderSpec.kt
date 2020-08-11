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
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID
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
    }
})
