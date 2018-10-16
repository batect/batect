/*
   Copyright 2017-2018 Charles Korn.

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

import batect.os.NativeMethodException
import batect.os.NativeMethods
import batect.os.ProcessOutput
import batect.os.ProcessRunner
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.withMessage
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import jnr.constants.platform.Errno
import jnr.posix.POSIX
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.FileDescriptor

object ConsoleInfoSpec : Spek({
    describe("a console information provider") {
        val logger by createLoggerForEachTest()

        describe("determining if STDIN is connected to a TTY") {
            val nativeMethods = mock<NativeMethods>()
            val processRunner = mock<ProcessRunner>()

            on("STDIN being connected to a TTY") {
                val posix = mock<POSIX> {
                    on { isatty(FileDescriptor.`in`) } doReturn true
                }

                val consoleInfo = ConsoleInfo(posix, nativeMethods, processRunner, emptyMap(), logger)

                it("returns true") {
                    assertThat(consoleInfo.stdinIsTTY, equalTo(true))
                }
            }

            on("STDIN not being connected to a TTY") {
                val posix = mock<POSIX> {
                    on { isatty(FileDescriptor.`in`) } doReturn false
                }

                val consoleInfo = ConsoleInfo(posix, nativeMethods, processRunner, emptyMap(), logger)

                it("returns false") {
                    assertThat(consoleInfo.stdinIsTTY, equalTo(false))
                }
            }
        }

        describe("determining if the console supports interactivity") {
            val nativeMethods = mock<NativeMethods>()
            val processRunner = mock<ProcessRunner>()

            describe("on STDIN being connected to a TTY") {
                val posix = mock<POSIX> {
                    on { isatty(FileDescriptor.`in`) } doReturn true
                }

                on("the TERM environment variable being set to 'dumb'") {
                    val consoleInfo = ConsoleInfo(posix, nativeMethods, processRunner, mapOf("TERM" to "dumb"), logger)

                    it("returns false") {
                        assertThat(consoleInfo.supportsInteractivity, equalTo(false))
                    }
                }

                on("the TERM environment variables not being set") {
                    val consoleInfo = ConsoleInfo(posix, nativeMethods, processRunner, emptyMap(), logger)

                    it("returns false") {
                        assertThat(consoleInfo.supportsInteractivity, equalTo(false))
                    }
                }

                on("the TERM environment variable being set to something other than 'dumb' and the TRAVIS environment variable not being set") {
                    val consoleInfo = ConsoleInfo(posix, nativeMethods, processRunner, mapOf("TERM" to "other-terminal"), logger)

                    it("returns true") {
                        assertThat(consoleInfo.supportsInteractivity, equalTo(true))
                    }
                }

                on("the TERM environment variable being set to something other than 'dumb' and the TRAVIS environment variable being set") {
                    val consoleInfo = ConsoleInfo(posix, nativeMethods, processRunner, mapOf("TERM" to "other-terminal", "TRAVIS" to "true"), logger)

                    it("returns false") {
                        assertThat(consoleInfo.supportsInteractivity, equalTo(false))
                    }
                }
            }

            on("STDIN not being connected to a TTY") {
                val posix = mock<POSIX> {
                    on { isatty(FileDescriptor.`in`) } doReturn false
                }

                val consoleInfo = ConsoleInfo(posix, nativeMethods, processRunner, mapOf("TERM" to "other-terminal"), logger)

                it("returns false") {
                    assertThat(consoleInfo.supportsInteractivity, equalTo(false))
                }
            }
        }

        describe("getting the type of terminal") {
            val posix = mock<POSIX>()
            val nativeMethods = mock<NativeMethods>()
            val processRunner = mock<ProcessRunner>()

            on("when the TERM environment variable is not set") {
                val consoleInfo = ConsoleInfo(posix, nativeMethods, processRunner, emptyMap(), logger)

                it("returns null") {
                    assertThat(consoleInfo.terminalType, absent())
                }
            }

            on("when the TERM environment variable is set") {
                val consoleInfo = ConsoleInfo(posix, nativeMethods, processRunner, mapOf("TERM" to "some-terminal"), logger)

                it("returns its value") {
                    assertThat(consoleInfo.terminalType, equalTo("some-terminal"))
                }
            }
        }

        describe("getting the dimensions of the terminal") {
            val posix = mock<POSIX>()
            val processRunner = mock<ProcessRunner>()

            on("getting the dimensions succeeding") {
                val nativeMethods = mock<NativeMethods> {
                    on { getConsoleDimensions() } doReturn Dimensions(51, 204)
                }

                val consoleInfo = ConsoleInfo(posix, nativeMethods, processRunner, emptyMap(), logger)

                it("returns a parsed set of dimensions") {
                    assertThat(consoleInfo.dimensions, equalTo(Dimensions(51, 204)))
                }
            }

            on("getting the dimensions failing because the console is not a TTY") {
                val nativeMethods = mock<NativeMethods> {
                    on { getConsoleDimensions() } doThrow NativeMethodException("ioctl", Errno.ENOTTY)
                }

                val consoleInfo = ConsoleInfo(posix, nativeMethods, processRunner, emptyMap(), logger)

                it("returns a null set of dimensions") {
                    assertThat(consoleInfo.dimensions, absent())
                }
            }

            on("getting the dimensions failing for another reason") {
                val nativeMethods = mock<NativeMethods> {
                    on { getConsoleDimensions() } doThrow NativeMethodException("ioctl", Errno.EBUSY)
                }

                val consoleInfo = ConsoleInfo(posix, nativeMethods, processRunner, emptyMap(), logger)

                it("propagates the exception") {
                    assertThat({ consoleInfo.dimensions }, throws<NativeMethodException>())
                }
            }
        }

        describe("entering and exiting raw mode") {
            val posix by createForEachTest { mock<POSIX>() }
            val nativeMethods by createForEachTest { mock<NativeMethods>() }
            val processRunner by createForEachTest { mock<ProcessRunner>() }
            val consoleInfo by createForEachTest { ConsoleInfo(posix, nativeMethods, processRunner, logger) }

            describe("entering raw mode") {
                given("the terminal is a TTY") {
                    beforeEachTest { whenever(posix.isatty(FileDescriptor.`in`)).doReturn(true) }

                    given("invoking 'stty -g' succeeds") {
                        beforeEachTest { whenever(processRunner.runAndCaptureOutput(listOf("stty", "-g"))).doReturn(ProcessOutput(0, "existing_terminal_state\n")) }

                        given("invoking 'stty raw' succeeds") {
                            beforeEachTest { whenever(processRunner.runAndCaptureOutput(listOf("stty", "raw"))).doReturn(ProcessOutput(0, "")) }

                            on("entering raw mode") {
                                val restorer = consoleInfo.enterRawMode()

                                it("calls stty to enter raw mode") {
                                    verify(processRunner).runAndCaptureOutput(listOf("stty", "raw"))
                                }

                                it("returns an object that can be used to restore the terminal to its previous state") {
                                    assertThat(restorer, equalTo(TerminalStateRestorer("existing_terminal_state", processRunner)))
                                }
                            }
                        }

                        given("invoking 'stty raw' fails") {
                            beforeEachTest { whenever(processRunner.runAndCaptureOutput(listOf("stty", "raw"))).doReturn(ProcessOutput(1, "Something went wrong.\n")) }

                            on("entering raw mode") {
                                it("throws an appropriate exception") {
                                    assertThat({ consoleInfo.enterRawMode() }, throws<RuntimeException>(withMessage("Invoking 'stty raw' failed with exit code 1: Something went wrong.")))
                                }
                            }
                        }
                    }

                    given("invoking 'stty -g' fails") {
                        beforeEachTest { whenever(processRunner.runAndCaptureOutput(listOf("stty", "-g"))).doReturn(ProcessOutput(1, "Something went wrong.\n")) }

                        on("entering raw mode") {
                            it("throws an appropriate exception") {
                                assertThat({ consoleInfo.enterRawMode() }, throws<RuntimeException>(withMessage("Invoking 'stty -g' failed with exit code 1: Something went wrong.")))
                            }
                        }
                    }
                }

                given("the terminal is not a TTY") {
                    beforeEachTest { whenever(posix.isatty(FileDescriptor.`in`)).doReturn(false) }

                    on("entering raw mode") {
                        it("throws an appropriate exception") {
                            assertThat({ consoleInfo.enterRawMode() }, throws<UnsupportedOperationException>(withMessage("Terminal is not a TTY.")))
                        }
                    }
                }
            }

            describe("exiting raw mode") {
                val restorer by createForEachTest { TerminalStateRestorer("some_old_state", processRunner) }

                given("invoking 'stty' succeeds'") {
                    beforeEachTest { whenever(processRunner.runAndCaptureOutput(listOf("stty", "some_old_state"))).doReturn(ProcessOutput(0, "")) }

                    on("exiting raw mode") {
                        restorer.close()

                        it("calls stty to restore the previous state") {
                            verify(processRunner).runAndCaptureOutput(listOf("stty", "some_old_state"))
                        }
                    }
                }

                given("invoking 'stty' fails") {
                    beforeEachTest { whenever(processRunner.runAndCaptureOutput(listOf("stty", "some_old_state"))).doReturn(ProcessOutput(1, "Something went wrong.\n")) }

                    on("exiting raw mode") {
                        it("throws an appropriate exception") {
                            assertThat({ restorer.close() }, throws<RuntimeException>(withMessage("Invoking 'stty some_old_state' failed with exit code 1: Something went wrong.")))
                        }
                    }
                }
            }
        }
    }
})
