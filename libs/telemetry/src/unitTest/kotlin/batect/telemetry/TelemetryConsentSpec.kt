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

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

object TelemetryConsentSpec : Spek({
    describe("telemetry consent") {
        data class TestCase(val disabledOnCommandLine: Boolean?, val consentStateOnDisk: ConsentState, val forbiddenByProjectConfig: Boolean, val expected: Boolean)

        setOf(
            TestCase(true, ConsentState.TelemetryDisabled, false, expected = false),
            TestCase(true, ConsentState.TelemetryAllowed, false, expected = false),
            TestCase(true, ConsentState.None, false, expected = false),
            TestCase(false, ConsentState.TelemetryDisabled, false, expected = true),
            TestCase(false, ConsentState.TelemetryAllowed, false, expected = true),
            TestCase(false, ConsentState.None, false, expected = true),
            TestCase(null, ConsentState.TelemetryDisabled, false, expected = false),
            TestCase(null, ConsentState.TelemetryAllowed, false, expected = true),
            TestCase(null, ConsentState.None, false, expected = false),
            TestCase(true, ConsentState.TelemetryDisabled, true, expected = false),
            TestCase(true, ConsentState.TelemetryAllowed, true, expected = false),
            TestCase(true, ConsentState.None, true, expected = false),
            TestCase(false, ConsentState.TelemetryDisabled, true, expected = false),
            TestCase(false, ConsentState.TelemetryAllowed, true, expected = false),
            TestCase(false, ConsentState.None, true, expected = false),
            TestCase(null, ConsentState.TelemetryDisabled, true, expected = false),
            TestCase(null, ConsentState.TelemetryAllowed, true, expected = false),
            TestCase(null, ConsentState.None, true, expected = false),
        ).forEach { (disabledOnCommandLine, consentStateOnDisk, forbiddenByProjectConfig, expected) ->
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

            val sessionDescription = if (forbiddenByProjectConfig) "forbidden by the project's configuration" else "not forbidden by the project's configuration"

            val configurationStore by createForEachTest {
                mock<TelemetryConfigurationStore> {
                    on { currentConfiguration } doReturn TelemetryConfiguration(UUID.randomUUID(), consentStateOnDisk)
                }
            }

            val consent by createForEachTest { TelemetryConsent(disabledOnCommandLine, configurationStore) }

            beforeEachTest {
                consent.forbiddenByProjectConfig = forbiddenByProjectConfig
            }

            given("telemetry is $commandLineDescription, $consentStateOnDiskDescription and $sessionDescription") {
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
