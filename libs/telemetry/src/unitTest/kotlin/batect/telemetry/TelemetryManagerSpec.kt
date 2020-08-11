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

import batect.testutils.createForEachTest
import batect.testutils.given
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TelemetryManagerSpec : Spek({
    describe("a telemetry manager") {
        val telemetryConsent by createForEachTest { mock<TelemetryConsent>() }
        val telemetryUploadQueue by createForEachTest { mock<TelemetryUploadQueue>() }
        val telemetryConfigurationStore by createForEachTest { mock<TelemetryConfigurationStore>() }
        val telemetryManager by createForEachTest { TelemetryManager(telemetryConsent, telemetryUploadQueue, telemetryConfigurationStore) }

        val session by createForEachTest { mock<TelemetrySession>() }
        val sessionBuilder by createForEachTest {
            mock<TelemetrySessionBuilder> {
                on { build(telemetryConfigurationStore) } doReturn session
            }
        }

        given("telemetry is disabled") {
            beforeEachTest {
                whenever(telemetryConsent.telemetryAllowed).thenReturn(false)

                telemetryManager.finishSession(sessionBuilder)
            }

            it("does not save the session to the upload queue") {
                verify(telemetryUploadQueue, never()).add(any())
            }
        }

        given("telemetry is enabled") {
            beforeEachTest {
                whenever(telemetryConsent.telemetryAllowed).thenReturn(true)

                telemetryManager.finishSession(sessionBuilder)
            }

            it("saves the session to the upload queue") {
                verify(telemetryUploadQueue).add(session)
            }
        }
    }
})
