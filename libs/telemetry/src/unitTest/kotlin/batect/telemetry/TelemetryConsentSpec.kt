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
import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

object TelemetryConsentSpec : Spek({
    describe("telemetry consent") {
        data class TestCase(val disabledOnCommandLine: Boolean?, val consentStateOnDisk: ConsentState, val expected: Boolean)

        setOf(
            TestCase(true, ConsentState.TelemetryDisabled, expected = false),
            TestCase(true, ConsentState.TelemetryAllowed, expected = false),
            TestCase(true, ConsentState.None, expected = false),
            TestCase(false, ConsentState.TelemetryDisabled, expected = true),
            TestCase(false, ConsentState.TelemetryAllowed, expected = true),
            TestCase(false, ConsentState.None, expected = true),
            TestCase(null, ConsentState.TelemetryDisabled, expected = false),
            TestCase(null, ConsentState.TelemetryAllowed, expected = true),
            TestCase(null, ConsentState.None, expected = false)
        ).forEach { (disabledOnCommandLine, consentStateOnDisk, expected) ->
            val commandLineDescription = when (disabledOnCommandLine) {
                true -> "explicitly disabled on the command line"
                false -> "explicitly enabled on the command line"
                null -> "not configured on the command line"
            }

            val consentStateOnDiskDescription = when (consentStateOnDisk) {
                ConsentState.TelemetryAllowed -> "allowed according to the configuration on disk"
                ConsentState.TelemetryDisabled -> "disabled according to the configuration on disk"
                ConsentState.None -> "there is no configuration on disk"
            }

            val configurationStore by createForEachTest {
                mock<TelemetryConfigurationStore> {
                    on { currentConfiguration } doReturn TelemetryConfiguration(UUID.randomUUID(), consentStateOnDisk)
                }
            }

            val consent by createForEachTest { TelemetryConsent(disabledOnCommandLine, configurationStore) }

            given("telemetry is $commandLineDescription and $consentStateOnDiskDescription") {
                val expectedDescription = when (expected) {
                    true -> "allows telemetry"
                    false -> "does not allow telemetry"
                }

                it(expectedDescription) {
                    assertThat(consent.telemetryAllowed, equalTo(expected))
                }
            }
        }
    }
})
