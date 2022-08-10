/*
    Copyright 2017-2022 Charles Korn.

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

import batect.os.ConsoleInfo
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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
    }
})
