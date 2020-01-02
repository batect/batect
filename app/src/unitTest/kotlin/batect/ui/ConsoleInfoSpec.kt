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

package batect.ui

import batect.os.NativeMethods
import batect.os.OperatingSystem
import batect.os.SystemInfo
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.on
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ConsoleInfoSpec : Spek({
    describe("a console information provider") {
        val logger by createLoggerForEachTest()
        val genericSystemInfo by createForEachTest {
            mock<SystemInfo> {
                on { operatingSystem } doReturn OperatingSystem.Other
            }
        }

        describe("determining if STDIN is connected to a TTY") {
            on("STDIN being connected to a TTY") {
                val nativeMethods = mock<NativeMethods> {
                    on { determineIfStdinIsTTY() } doReturn true
                }

                val consoleInfo by createForEachTest { ConsoleInfo(nativeMethods, genericSystemInfo, emptyMap(), logger) }

                it("returns true") {
                    assertThat(consoleInfo.stdinIsTTY, equalTo(true))
                }
            }

            on("STDIN not being connected to a TTY") {
                val nativeMethods = mock<NativeMethods> {
                    on { determineIfStdinIsTTY() } doReturn false
                }

                val consoleInfo by createForEachTest { ConsoleInfo(nativeMethods, genericSystemInfo, emptyMap(), logger) }

                it("returns false") {
                    assertThat(consoleInfo.stdinIsTTY, equalTo(false))
                }
            }
        }

        describe("determining if STDOUT is connected to a TTY") {
            on("STDOUT being connected to a TTY") {
                val nativeMethods = mock<NativeMethods> {
                    on { determineIfStdoutIsTTY() } doReturn true
                }

                val consoleInfo by createForEachTest { ConsoleInfo(nativeMethods, genericSystemInfo, emptyMap(), logger) }

                it("returns true") {
                    assertThat(consoleInfo.stdoutIsTTY, equalTo(true))
                }
            }

            on("STDOUT not being connected to a TTY") {
                val nativeMethods = mock<NativeMethods> {
                    on { determineIfStdoutIsTTY() } doReturn false
                }

                val consoleInfo by createForEachTest { ConsoleInfo(nativeMethods, genericSystemInfo, emptyMap(), logger) }

                it("returns false") {
                    assertThat(consoleInfo.stdoutIsTTY, equalTo(false))
                }
            }
        }

        describe("determining if the console supports interactivity") {
            describe("on STDOUT being connected to a TTY") {
                val nativeMethods = mock<NativeMethods> {
                    on { determineIfStdoutIsTTY() } doReturn true
                }

                on("the TERM environment variable being set to 'dumb'") {
                    val consoleInfo by createForEachTest { ConsoleInfo(nativeMethods, genericSystemInfo, mapOf("TERM" to "dumb"), logger) }

                    it("returns false") {
                        assertThat(consoleInfo.supportsInteractivity, equalTo(false))
                    }
                }

                describe("on the TERM environment variable not being set") {
                    describe("when the application is running on Windows") {
                        val windowsSystemInfo by createForEachTest {
                            mock<SystemInfo> {
                                on { operatingSystem } doReturn OperatingSystem.Windows
                            }
                        }

                        val consoleInfo by createForEachTest { ConsoleInfo(nativeMethods, windowsSystemInfo, emptyMap(), logger) }

                        it("returns true") {
                            assertThat(consoleInfo.supportsInteractivity, equalTo(true))
                        }
                    }

                    describe("when the application is not running on Windows") {
                        val consoleInfo by createForEachTest { ConsoleInfo(nativeMethods, genericSystemInfo, emptyMap(), logger) }

                        it("returns false") {
                            assertThat(consoleInfo.supportsInteractivity, equalTo(false))
                        }
                    }
                }

                on("the TERM environment variable being set to something other than 'dumb' and the TRAVIS environment variable not being set") {
                    val consoleInfo by createForEachTest { ConsoleInfo(nativeMethods, genericSystemInfo, mapOf("TERM" to "other-terminal"), logger) }

                    it("returns true") {
                        assertThat(consoleInfo.supportsInteractivity, equalTo(true))
                    }
                }

                on("the TERM environment variable being set to something other than 'dumb' and the TRAVIS environment variable being set") {
                    val consoleInfo by createForEachTest { ConsoleInfo(nativeMethods, genericSystemInfo, mapOf("TERM" to "other-terminal", "TRAVIS" to "true"), logger) }

                    it("returns false") {
                        assertThat(consoleInfo.supportsInteractivity, equalTo(false))
                    }
                }
            }

            on("STDOUT not being connected to a TTY") {
                val nativeMethods = mock<NativeMethods> {
                    on { determineIfStdoutIsTTY() } doReturn false
                }

                val consoleInfo by createForEachTest { ConsoleInfo(nativeMethods, genericSystemInfo, mapOf("TERM" to "other-terminal"), logger) }

                it("returns false") {
                    assertThat(consoleInfo.supportsInteractivity, equalTo(false))
                }
            }
        }

        describe("getting the type of terminal") {
            val nativeMethods = mock<NativeMethods>()

            on("when the TERM environment variable is not set") {
                val consoleInfo by createForEachTest { ConsoleInfo(nativeMethods, genericSystemInfo, emptyMap(), logger) }

                it("returns null") {
                    assertThat(consoleInfo.terminalType, absent())
                }
            }

            on("when the TERM environment variable is set") {
                val consoleInfo by createForEachTest { ConsoleInfo(nativeMethods, genericSystemInfo, mapOf("TERM" to "some-terminal"), logger) }

                it("returns its value") {
                    assertThat(consoleInfo.terminalType, equalTo("some-terminal"))
                }
            }
        }
    }
})
