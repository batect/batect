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
import batect.telemetry.ConsentStateStore
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.runForEachTest
import batect.ui.Console
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.util.UUID
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object EnableTelemetryCommandSpec : Spek({
    describe("an 'enable telemetry' command") {
        val consentStateStore by createForEachTest { mock<ConsentStateStore>() }
        val console by createForEachTest { mock<Console>() }
        val command by createForEachTest { EnableTelemetryCommand(consentStateStore, console) }

        describe("running the command") {
            given("telemetry is not already enabled") {
                beforeEachTest {
                    whenever(consentStateStore.consentState).doReturn(ConsentState.None)
                }

                val exitCode by runForEachTest { command.run() }

                it("returns a zero exit code") {
                    assertThat(exitCode, equalTo(0))
                }

                it("prints a success message to the console after enabling telemetry") {
                    inOrder(consentStateStore, console) {
                        verify(consentStateStore).saveConsentState(isA<ConsentState.Enabled>())
                        verify(console).println("Telemetry successfully enabled.")
                    }
                }
            }

            given("telemetry is already enabled") {
                beforeEachTest {
                    whenever(consentStateStore.consentState).doReturn(ConsentState.Enabled(UUID.randomUUID()))
                }

                val exitCode by runForEachTest { command.run() }

                it("returns a zero exit code") {
                    assertThat(exitCode, equalTo(0))
                }

                it("prints a message to the console") {
                    verify(console).println("Telemetry already enabled.")
                }

                it("does not save a new consent state") {
                    verify(consentStateStore, never()).saveConsentState(any())
                }
            }
        }
    }
})
