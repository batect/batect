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

package batect.os.windows

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.os.ConsoleInfo
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object WindowsConsoleManagerSpec : Spek({
    describe("a Windows console manager") {
        val consoleInfo by createForEachTest { mock<ConsoleInfo>() }
        val nativeMethods by createForEachTest { mock<WindowsNativeMethods>() }
        val logger by createLoggerForEachTestWithoutCustomSerializers()
        val consoleManager by createForEachTest { WindowsConsoleManager(consoleInfo, nativeMethods, logger) }

        describe("enabling console escape sequences") {
            given("stdout is a TTY") {
                beforeEachTest { whenever(consoleInfo.stdoutIsTTY).doReturn(true) }
                beforeEachTest { consoleManager.enableConsoleEscapeSequences() }

                it("calls the Windows API to enable console escape sequences") {
                    verify(nativeMethods).enableConsoleEscapeSequences()
                }
            }

            given("stdout is not a TTY") {
                beforeEachTest { whenever(consoleInfo.stdoutIsTTY).doReturn(false) }
                beforeEachTest { consoleManager.enableConsoleEscapeSequences() }

                it("does not call the Windows API to enable console escape sequences") {
                    verify(nativeMethods, never()).enableConsoleEscapeSequences()
                }
            }
        }

        describe("entering and exiting raw mode") {
            given("the terminal is a TTY") {
                beforeEachTest { whenever(consoleInfo.stdinIsTTY).doReturn(true) }

                describe("entering raw mode") {
                    beforeEachTest { whenever(nativeMethods.enableConsoleRawMode()).thenReturn(123) }

                    val restorer by runForEachTest { consoleManager.enterRawMode() }

                    it("calls the Windows API to enter raw mode") {
                        verify(nativeMethods).enableConsoleRawMode()
                    }

                    it("returns an object that can be used to restore the terminal to its previous state") {
                        assertThat(restorer, equalTo(TerminalStateRestorer(123, nativeMethods)))
                    }
                }

                describe("exiting raw mode") {
                    val restorer by createForEachTest { TerminalStateRestorer(456, nativeMethods) }

                    beforeEachTest { restorer.close() }

                    it("calls the Windows API to restore the previous state") {
                        verify(nativeMethods).restoreConsoleMode(456)
                    }
                }
            }

            given("the terminal is not a TTY") {
                beforeEachTest { whenever(consoleInfo.stdinIsTTY).doReturn(false) }

                on("entering raw mode") {
                    beforeEachTest { consoleManager.enterRawMode() }

                    it("does not invoke the Windows API") {
                        verify(nativeMethods, never()).enableConsoleRawMode()
                    }
                }

                on("exiting raw mode") {
                    beforeEachTest { consoleManager.enterRawMode().use { } }

                    it("does not invoke the Windows API") {
                        verify(nativeMethods, never()).restoreConsoleMode(any())
                    }
                }
            }
        }
    }
})
