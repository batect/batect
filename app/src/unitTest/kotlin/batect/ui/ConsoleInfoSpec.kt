/*
   Copyright 2017-2019 Charles Korn.

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

import batect.os.ProcessOutput
import batect.os.ProcessRunner
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import jnr.posix.POSIX
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.FileDescriptor

object ConsoleInfoSpec : Spek({
    describe("a console information provider") {
        val logger by createLoggerForEachTest()

        describe("determining if STDIN is connected to a TTY") {
            val processRunner = mock<ProcessRunner>()

            on("STDIN being connected to a TTY") {
                val posix = mock<POSIX> {
                    on { isatty(FileDescriptor.`in`) } doReturn true
                }

                val consoleInfo by createForEachTest { ConsoleInfo(posix, processRunner, emptyMap(), logger) }

                it("returns true") {
                    assertThat(consoleInfo.stdinIsTTY, equalTo(true))
                }
            }

            on("STDIN not being connected to a TTY") {
                val posix = mock<POSIX> {
                    on { isatty(FileDescriptor.`in`) } doReturn false
                }

                val consoleInfo by createForEachTest { ConsoleInfo(posix, processRunner, emptyMap(), logger) }

                it("returns false") {
                    assertThat(consoleInfo.stdinIsTTY, equalTo(false))
                }
            }
        }

        describe("determining if the console supports interactivity") {
            val processRunner = mock<ProcessRunner>()

            describe("on STDIN being connected to a TTY") {
                val posix = mock<POSIX> {
                    on { isatty(FileDescriptor.`in`) } doReturn true
                }

                on("the TERM environment variable being set to 'dumb'") {
                    val consoleInfo by createForEachTest { ConsoleInfo(posix, processRunner, mapOf("TERM" to "dumb"), logger) }

                    it("returns false") {
                        assertThat(consoleInfo.supportsInteractivity, equalTo(false))
                    }
                }

                on("the TERM environment variables not being set") {
                    val consoleInfo by createForEachTest { ConsoleInfo(posix, processRunner, emptyMap(), logger) }

                    it("returns false") {
                        assertThat(consoleInfo.supportsInteractivity, equalTo(false))
                    }
                }

                on("the TERM environment variable being set to something other than 'dumb' and the TRAVIS environment variable not being set") {
                    val consoleInfo by createForEachTest { ConsoleInfo(posix, processRunner, mapOf("TERM" to "other-terminal"), logger) }

                    it("returns true") {
                        assertThat(consoleInfo.supportsInteractivity, equalTo(true))
                    }
                }

                on("the TERM environment variable being set to something other than 'dumb' and the TRAVIS environment variable being set") {
                    val consoleInfo by createForEachTest { ConsoleInfo(posix, processRunner, mapOf("TERM" to "other-terminal", "TRAVIS" to "true"), logger) }

                    it("returns false") {
                        assertThat(consoleInfo.supportsInteractivity, equalTo(false))
                    }
                }
            }

            on("STDIN not being connected to a TTY") {
                val posix = mock<POSIX> {
                    on { isatty(FileDescriptor.`in`) } doReturn false
                }

                val consoleInfo by createForEachTest { ConsoleInfo(posix, processRunner, mapOf("TERM" to "other-terminal"), logger) }

                it("returns false") {
                    assertThat(consoleInfo.supportsInteractivity, equalTo(false))
                }
            }
        }

        describe("getting the type of terminal") {
            val posix = mock<POSIX>()
            val processRunner = mock<ProcessRunner>()

            on("when the TERM environment variable is not set") {
                val consoleInfo by createForEachTest { ConsoleInfo(posix, processRunner, emptyMap(), logger) }

                it("returns null") {
                    assertThat(consoleInfo.terminalType, absent())
                }
            }

            on("when the TERM environment variable is set") {
                val consoleInfo by createForEachTest { ConsoleInfo(posix, processRunner, mapOf("TERM" to "some-terminal"), logger) }

                it("returns its value") {
                    assertThat(consoleInfo.terminalType, equalTo("some-terminal"))
                }
            }
        }

        describe("entering and exiting raw mode") {
            val posix by createForEachTest { mock<POSIX>() }
            val processRunner by createForEachTest { mock<ProcessRunner>() }
            val consoleInfo by createForEachTest { ConsoleInfo(posix, processRunner, logger) }

            given("the terminal is a TTY") {
                beforeEachTest { whenever(posix.isatty(FileDescriptor.`in`)).doReturn(true) }

                describe("entering raw mode") {
                    given("invoking 'stty -g' succeeds") {
                        val rawCommand = listOf("stty", "-ignbrk", "-brkint", "-parmrk", "-istrip", "-inlcr", "-igncr", "-icrnl", "-ixon", "-opost", "-echo", "-echonl",
                            "-icanon", "-isig", "-iexten", "-parenb", "cs8", "min", "1", "time", "0")

                        beforeEachTest { whenever(processRunner.runWithStdinAttached(listOf("stty", "-g"))).doReturn(ProcessOutput(0, "existing_terminal_state\n")) }

                        given("invoking 'stty raw' succeeds") {
                            beforeEachTest { whenever(processRunner.runWithStdinAttached(rawCommand)).doReturn(ProcessOutput(0, "")) }

                            on("entering raw mode") {
                                val restorer by runForEachTest { consoleInfo.enterRawMode() }

                                it("calls stty to enter raw mode") {
                                    verify(processRunner).runWithStdinAttached(rawCommand)
                                }

                                it("returns an object that can be used to restore the terminal to its previous state") {
                                    assertThat(restorer, equalTo(TerminalStateRestorer("existing_terminal_state", processRunner)))
                                }
                            }
                        }

                        given("invoking 'stty raw' fails") {
                            beforeEachTest { whenever(processRunner.runWithStdinAttached(rawCommand)).doReturn(ProcessOutput(1, "Something went wrong.\n")) }

                            on("entering raw mode") {
                                it("throws an appropriate exception") {
                                    assertThat({ consoleInfo.enterRawMode() }, throws<RuntimeException>(withMessage("Invoking '${rawCommand.joinToString(" ")}' failed with exit code 1: Something went wrong.")))
                                }
                            }
                        }
                    }

                    given("invoking 'stty -g' fails") {
                        beforeEachTest { whenever(processRunner.runWithStdinAttached(listOf("stty", "-g"))).doReturn(ProcessOutput(1, "Something went wrong.\n")) }

                        on("entering raw mode") {
                            it("throws an appropriate exception") {
                                assertThat({ consoleInfo.enterRawMode() }, throws<RuntimeException>(withMessage("Invoking 'stty -g' failed with exit code 1: Something went wrong.")))
                            }
                        }
                    }
                }

                describe("exiting raw mode") {
                    val restorer by createForEachTest { TerminalStateRestorer("some_old_state", processRunner) }

                    given("invoking 'stty' succeeds'") {
                        beforeEachTest { whenever(processRunner.runWithStdinAttached(listOf("stty", "some_old_state"))).doReturn(ProcessOutput(0, "")) }

                        on("exiting raw mode") {
                            beforeEachTest { restorer.close() }

                            it("calls stty to restore the previous state") {
                                verify(processRunner).runWithStdinAttached(listOf("stty", "some_old_state"))
                            }
                        }
                    }

                    given("invoking 'stty' fails") {
                        beforeEachTest { whenever(processRunner.runWithStdinAttached(listOf("stty", "some_old_state"))).doReturn(ProcessOutput(1, "Something went wrong.\n")) }

                        on("exiting raw mode") {
                            it("throws an appropriate exception") {
                                assertThat({ restorer.close() }, throws<RuntimeException>(withMessage("Invoking 'stty some_old_state' failed with exit code 1: Something went wrong.")))
                            }
                        }
                    }
                }
            }

            given("the terminal is not a TTY") {
                beforeEachTest { whenever(posix.isatty(FileDescriptor.`in`)).doReturn(false) }

                on("entering raw mode") {
                    beforeEachTest { consoleInfo.enterRawMode() }

                    it("does not invoke any external processes") {
                        verify(processRunner, never()).runWithStdinAttached(any())
                    }
                }

                on("exiting raw mode") {
                    beforeEachTest { consoleInfo.enterRawMode().use { } }

                    it("does not invoke any external processes") {
                        verify(processRunner, never()).runWithStdinAttached(any())
                    }
                }
            }
        }
    }
})
