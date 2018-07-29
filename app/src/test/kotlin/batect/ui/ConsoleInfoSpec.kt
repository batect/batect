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

import batect.os.ProcessOutput
import batect.os.ProcessRunner
import batect.testutils.createLoggerForEachTest
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ConsoleInfoSpec : Spek({
    describe("a console information provider") {
        val logger by createLoggerForEachTest()

        describe("determining if STDIN is connected to a TTY") {
            on("STDIN being connected to a TTY") {
                val processRunner = mock<ProcessRunner>() {
                    on { runAndCaptureOutput(listOf("tty")) } doReturn ProcessOutput(0, "/dev/pts/0")
                }

                val consoleInfo = ConsoleInfo(processRunner, emptyMap(), logger)

                it("returns true") {
                    assertThat(consoleInfo.stdinIsTTY, equalTo(true))
                }
            }

            on("STDIN not being connected to a TTY") {
                val processRunner = mock<ProcessRunner>() {
                    on { runAndCaptureOutput(listOf("tty")) } doReturn ProcessOutput(1, "not a tty")
                }

                val consoleInfo = ConsoleInfo(processRunner, emptyMap(), logger)

                it("returns false") {
                    assertThat(consoleInfo.stdinIsTTY, equalTo(false))
                }
            }
        }

        describe("determining if the console supports interactivity") {
            describe("on STDIN being connected to a TTY") {
                val processRunner = mock<ProcessRunner>() {
                    on { runAndCaptureOutput(listOf("tty")) } doReturn ProcessOutput(0, "/dev/pts/0")
                }

                on("the TERM environment variable being set to 'dumb'") {
                    val consoleInfo = ConsoleInfo(processRunner, mapOf("TERM" to "dumb"), logger)

                    it("returns false") {
                        assertThat(consoleInfo.supportsInteractivity, equalTo(false))
                    }
                }

                on("the TERM environment variables not being set") {
                    val consoleInfo = ConsoleInfo(processRunner, emptyMap(), logger)

                    it("returns false") {
                        assertThat(consoleInfo.supportsInteractivity, equalTo(false))
                    }
                }

                on("the TERM environment variable being set to something other than 'dumb' and the TRAVIS environment variable not being set") {
                    val consoleInfo = ConsoleInfo(processRunner, mapOf("TERM" to "other-terminal"), logger)

                    it("returns true") {
                        assertThat(consoleInfo.supportsInteractivity, equalTo(true))
                    }
                }

                on("the TERM environment variable being set to something other than 'dumb' and the TRAVIS environment variable being set") {
                    val consoleInfo = ConsoleInfo(processRunner, mapOf("TERM" to "other-terminal", "TRAVIS" to "true"), logger)

                    it("returns false") {
                        assertThat(consoleInfo.supportsInteractivity, equalTo(false))
                    }
                }
            }

            on("STDIN not being connected to a TTY") {
                val processRunner = mock<ProcessRunner>() {
                    on { runAndCaptureOutput(listOf("tty")) } doReturn ProcessOutput(1, "not a tty")
                }

                val consoleInfo = ConsoleInfo(processRunner, mapOf("TERM" to "other-terminal"), logger)

                it("returns false") {
                    assertThat(consoleInfo.supportsInteractivity, equalTo(false))
                }
            }
        }

        describe("getting the type of terminal") {
            val processRunner = mock<ProcessRunner>()

            on("when the TERM environment variable is not set") {
                val consoleInfo = ConsoleInfo(processRunner, emptyMap(), logger)

                it("returns null") {
                    assertThat(consoleInfo.terminalType, absent())
                }
            }

            on("when the TERM environment variable is set") {
                val consoleInfo = ConsoleInfo(processRunner, mapOf("TERM" to "some-terminal"), logger)

                it("returns its value") {
                    assertThat(consoleInfo.terminalType, equalTo("some-terminal"))
                }
            }
        }

        describe("getting the dimensions of the terminal") {
            on("getting the dimensions succeeding") {
                val processRunner = mock<ProcessRunner>() {
                    on { runAndCaptureOutput(listOf("stty", "size")) } doReturn ProcessOutput(0, "51 204\n")
                }

                val consoleInfo = ConsoleInfo(processRunner, emptyMap(), logger)

                it("returns a parsed set of dimensions") {
                    assertThat(consoleInfo.dimensions, equalTo(Dimensions(height = 51, width = 204)))
                }
            }

            on("getting the dimensions failing") {
                val processRunner = mock<ProcessRunner>() {
                    on { runAndCaptureOutput(listOf("stty", "size")) } doReturn ProcessOutput(1, "something went wrong")
                }

                val consoleInfo = ConsoleInfo(processRunner, emptyMap(), logger)

                it("returns a null set of dimensions") {
                    assertThat(consoleInfo.dimensions, absent())
                }
            }
        }
    }
})
