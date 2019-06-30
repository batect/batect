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

import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.on
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import jnr.posix.POSIX
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.FileDescriptor

object ConsoleInfoSpec : Spek({
    describe("a console information provider") {
        val logger by createLoggerForEachTest()

        describe("determining if STDIN is connected to a TTY") {
            on("STDIN being connected to a TTY") {
                val posix = mock<POSIX> {
                    on { isatty(FileDescriptor.`in`) } doReturn true
                }

                val consoleInfo by createForEachTest { ConsoleInfo(posix, emptyMap(), logger) }

                it("returns true") {
                    assertThat(consoleInfo.stdinIsTTY, equalTo(true))
                }
            }

            on("STDIN not being connected to a TTY") {
                val posix = mock<POSIX> {
                    on { isatty(FileDescriptor.`in`) } doReturn false
                }

                val consoleInfo by createForEachTest { ConsoleInfo(posix, emptyMap(), logger) }

                it("returns false") {
                    assertThat(consoleInfo.stdinIsTTY, equalTo(false))
                }
            }
        }

        describe("determining if STDOUT is connected to a TTY") {
            on("STDOUT being connected to a TTY") {
                val posix = mock<POSIX> {
                    on { isatty(FileDescriptor.`out`) } doReturn true
                }

                val consoleInfo by createForEachTest { ConsoleInfo(posix, emptyMap(), logger) }

                it("returns true") {
                    assertThat(consoleInfo.stdoutIsTTY, equalTo(true))
                }
            }

            on("STDOUT not being connected to a TTY") {
                val posix = mock<POSIX> {
                    on { isatty(FileDescriptor.`out`) } doReturn false
                }

                val consoleInfo by createForEachTest { ConsoleInfo(posix, emptyMap(), logger) }

                it("returns false") {
                    assertThat(consoleInfo.stdoutIsTTY, equalTo(false))
                }
            }
        }

        describe("determining if the console supports interactivity") {
            describe("on STDIN being connected to a TTY") {
                val posix = mock<POSIX> {
                    on { isatty(FileDescriptor.`in`) } doReturn true
                }

                on("the TERM environment variable being set to 'dumb'") {
                    val consoleInfo by createForEachTest { ConsoleInfo(posix, mapOf("TERM" to "dumb"), logger) }

                    it("returns false") {
                        assertThat(consoleInfo.supportsInteractivity, equalTo(false))
                    }
                }

                on("the TERM environment variables not being set") {
                    val consoleInfo by createForEachTest { ConsoleInfo(posix, emptyMap(), logger) }

                    it("returns false") {
                        assertThat(consoleInfo.supportsInteractivity, equalTo(false))
                    }
                }

                on("the TERM environment variable being set to something other than 'dumb' and the TRAVIS environment variable not being set") {
                    val consoleInfo by createForEachTest { ConsoleInfo(posix, mapOf("TERM" to "other-terminal"), logger) }

                    it("returns true") {
                        assertThat(consoleInfo.supportsInteractivity, equalTo(true))
                    }
                }

                on("the TERM environment variable being set to something other than 'dumb' and the TRAVIS environment variable being set") {
                    val consoleInfo by createForEachTest { ConsoleInfo(posix, mapOf("TERM" to "other-terminal", "TRAVIS" to "true"), logger) }

                    it("returns false") {
                        assertThat(consoleInfo.supportsInteractivity, equalTo(false))
                    }
                }
            }

            on("STDIN not being connected to a TTY") {
                val posix = mock<POSIX> {
                    on { isatty(FileDescriptor.`in`) } doReturn false
                }

                val consoleInfo by createForEachTest { ConsoleInfo(posix, mapOf("TERM" to "other-terminal"), logger) }

                it("returns false") {
                    assertThat(consoleInfo.supportsInteractivity, equalTo(false))
                }
            }
        }

        describe("getting the type of terminal") {
            val posix = mock<POSIX>()

            on("when the TERM environment variable is not set") {
                val consoleInfo by createForEachTest { ConsoleInfo(posix, emptyMap(), logger) }

                it("returns null") {
                    assertThat(consoleInfo.terminalType, absent())
                }
            }

            on("when the TERM environment variable is set") {
                val consoleInfo by createForEachTest { ConsoleInfo(posix, mapOf("TERM" to "some-terminal"), logger) }

                it("returns its value") {
                    assertThat(consoleInfo.terminalType, equalTo("some-terminal"))
                }
            }
        }
    }
})
