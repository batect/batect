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

package batect.os.unix

import batect.os.ProcessOutput
import batect.os.ProcessRunner
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import batect.os.ConsoleInfo
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object UnixConsoleManagerSpec : Spek({
    describe("a Unix console manager") {
        val consoleInfo by createForEachTest { mock<ConsoleInfo>() }
        val applicationResolver by createForEachTest {
            mock<ApplicationResolver> {
                on { stty } doReturn "/bin/blah/stty"
            }
        }

        val processRunner by createForEachTest { mock<ProcessRunner>() }
        val logger by createLoggerForEachTestWithoutCustomSerializers()
        val consoleManager by createForEachTest { UnixConsoleManager(consoleInfo, applicationResolver, processRunner, logger) }

        describe("entering and exiting raw mode") {
            given("the terminal is a TTY") {
                beforeEachTest { whenever(consoleInfo.stdinIsTTY).doReturn(true) }

                describe("entering raw mode") {
                    given("invoking 'stty -g' succeeds") {
                        val rawCommand = listOf(
                            "/bin/blah/stty", "-ignbrk", "-brkint", "-parmrk", "-istrip", "-inlcr", "-igncr", "-icrnl", "-ixon", "-opost", "-echo", "-echonl",
                            "-icanon", "-isig", "-iexten", "-parenb", "cs8", "min", "1", "time", "0"
                        )

                        beforeEachTest { whenever(processRunner.runWithStdinAttached(listOf("/bin/blah/stty", "-g"))).doReturn(ProcessOutput(0, "existing_terminal_state\n")) }

                        given("invoking 'stty raw' succeeds") {
                            beforeEachTest { whenever(processRunner.runWithStdinAttached(rawCommand)).doReturn(ProcessOutput(0, "")) }

                            on("entering raw mode") {
                                val restorer by runForEachTest { consoleManager.enterRawMode() }

                                it("calls stty to enter raw mode") {
                                    verify(processRunner).runWithStdinAttached(rawCommand)
                                }

                                it("returns an object that can be used to restore the terminal to its previous state") {
                                    assertThat(restorer, equalTo(TerminalStateRestorer("/bin/blah/stty", "existing_terminal_state", processRunner)))
                                }
                            }
                        }

                        given("invoking 'stty raw' fails") {
                            beforeEachTest { whenever(processRunner.runWithStdinAttached(rawCommand)).doReturn(ProcessOutput(1, "Something went wrong.\n")) }

                            on("entering raw mode") {
                                it("throws an appropriate exception") {
                                    assertThat({ consoleManager.enterRawMode() }, throws<RuntimeException>(withMessage("Invoking '${rawCommand.joinToString(" ")}' failed with exit code 1: Something went wrong.")))
                                }
                            }
                        }
                    }

                    given("invoking 'stty -g' fails") {
                        beforeEachTest { whenever(processRunner.runWithStdinAttached(listOf("/bin/blah/stty", "-g"))).doReturn(ProcessOutput(1, "Something went wrong.\n")) }

                        on("entering raw mode") {
                            it("throws an appropriate exception") {
                                assertThat({ consoleManager.enterRawMode() }, throws<RuntimeException>(withMessage("Invoking '/bin/blah/stty -g' failed with exit code 1: Something went wrong.")))
                            }
                        }
                    }
                }

                describe("exiting raw mode") {
                    val restorer by createForEachTest { TerminalStateRestorer("/bin/blah/stty", "some_old_state", processRunner) }

                    given("invoking 'stty' succeeds'") {
                        beforeEachTest { whenever(processRunner.runWithStdinAttached(listOf("/bin/blah/stty", "some_old_state"))).doReturn(ProcessOutput(0, "")) }

                        on("exiting raw mode") {
                            beforeEachTest { restorer.close() }

                            it("calls stty to restore the previous state") {
                                verify(processRunner).runWithStdinAttached(listOf("/bin/blah/stty", "some_old_state"))
                            }
                        }
                    }

                    given("invoking 'stty' fails") {
                        beforeEachTest { whenever(processRunner.runWithStdinAttached(listOf("/bin/blah/stty", "some_old_state"))).doReturn(ProcessOutput(1, "Something went wrong.\n")) }

                        on("exiting raw mode") {
                            it("throws an appropriate exception") {
                                assertThat({ restorer.close() }, throws<RuntimeException>(withMessage("Invoking '/bin/blah/stty some_old_state' failed with exit code 1: Something went wrong.")))
                            }
                        }
                    }
                }
            }

            given("the terminal is not a TTY") {
                beforeEachTest { whenever(consoleInfo.stdinIsTTY).doReturn(false) }

                on("entering raw mode") {
                    beforeEachTest { consoleManager.enterRawMode() }

                    it("does not invoke any external processes") {
                        verify(processRunner, never()).runWithStdinAttached(any())
                    }
                }

                on("exiting raw mode") {
                    beforeEachTest { consoleManager.enterRawMode().use { } }

                    it("does not invoke any external processes") {
                        verify(processRunner, never()).runWithStdinAttached(any())
                    }
                }
            }
        }
    }
})
