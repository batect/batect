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

package batect.cli.commands

import batect.telemetry.ConsentState
import batect.telemetry.TelemetryConfiguration
import batect.telemetry.TelemetryConfigurationStore
import batect.telemetry.TelemetryUploadQueue
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.osIndependentPath
import batect.testutils.runForEachTest
import batect.ui.Console
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID

object DisableTelemetryCommandSpec : Spek({
    describe("a 'disable telemetry' command") {
        val existingConfiguration = TelemetryConfiguration(UUID.randomUUID(), ConsentState.TelemetryAllowed)
        val configurationStore by createForEachTest {
            mock<TelemetryConfigurationStore> {
                on { currentConfiguration } doReturn existingConfiguration
            }
        }

        val queuedSession1 by createForEachTest { osIndependentPath("session-1.json") }
        val queuedSession2 by createForEachTest { osIndependentPath("session-2.json") }
        val uploadQueue by createForEachTest {
            mock<TelemetryUploadQueue> {
                on { getAll() } doReturn setOf(queuedSession1, queuedSession2)
            }
        }

        val console by createForEachTest { mock<Console>() }
        val command by createForEachTest { DisableTelemetryCommand(configurationStore, uploadQueue, console) }

        describe("running the command") {
            val exitCode by runForEachTest { command.run() }

            it("returns a zero exit code") {
                assertThat(exitCode, equalTo(0))
            }

            it("disables telemetry") {
                verify(configurationStore).saveConfiguration(argThat { state == ConsentState.TelemetryDisabled })
            }

            it("generates a new user ID") {
                verify(configurationStore).saveConfiguration(argThat { userId != existingConfiguration.userId })
            }

            it("removes all queued sessions") {
                verify(uploadQueue).pop(queuedSession1)
                verify(uploadQueue).pop(queuedSession2)
            }

            it("prints messages to the console as it disables telemetry") {
                inOrder(console, configurationStore, uploadQueue) {
                    verify(console).println("Disabling telemetry and removing user ID...")
                    verify(configurationStore).saveConfiguration(any())

                    verify(console).println("Removing any cached telemetry data not yet uploaded...")
                    verify(uploadQueue, times(2)).pop(any())

                    verify(console).println("Telemetry successfully disabled.")
                }
            }
        }
    }
})
