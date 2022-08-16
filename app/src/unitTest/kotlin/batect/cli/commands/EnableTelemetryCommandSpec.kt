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

package batect.cli.commands

import batect.telemetry.ConsentState
import batect.telemetry.TelemetryConfiguration
import batect.telemetry.TelemetryConfigurationStore
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.runForEachTest
import batect.ui.Console
import com.natpryce.hamkrest.assertion.assertThat
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

object EnableTelemetryCommandSpec : Spek({
    describe("an 'enable telemetry' command") {
        val configurationStore by createForEachTest { mock<TelemetryConfigurationStore>() }
        val console by createForEachTest { mock<Console>() }
        val command by createForEachTest { EnableTelemetryCommand(configurationStore, console) }

        describe("running the command") {
            given("telemetry is not already enabled") {
                val existingConfiguration = TelemetryConfiguration(UUID.randomUUID(), ConsentState.None)
                val expectedConfiguration = TelemetryConfiguration(existingConfiguration.userId, ConsentState.TelemetryAllowed)

                beforeEachTest {
                    whenever(configurationStore.currentConfiguration).doReturn(existingConfiguration)
                }

                val exitCode by runForEachTest { command.run() }

                it("returns a zero exit code") {
                    assertThat(exitCode, equalTo(0))
                }

                it("prints a success message to the console after enabling telemetry") {
                    inOrder(configurationStore, console) {
                        verify(configurationStore).saveConfiguration(expectedConfiguration)
                        verify(console).println("Telemetry successfully enabled.")
                    }
                }
            }

            given("telemetry is already enabled") {
                beforeEachTest {
                    whenever(configurationStore.currentConfiguration).doReturn(TelemetryConfiguration(UUID.randomUUID(), ConsentState.TelemetryAllowed))
                }

                val exitCode by runForEachTest { command.run() }

                it("returns a zero exit code") {
                    assertThat(exitCode, equalTo(0))
                }

                it("prints a message to the console") {
                    verify(console).println("Telemetry already enabled.")
                }

                it("does not save a new telemetry configuration") {
                    verify(configurationStore, never()).saveConfiguration(any())
                }
            }
        }
    }
})
