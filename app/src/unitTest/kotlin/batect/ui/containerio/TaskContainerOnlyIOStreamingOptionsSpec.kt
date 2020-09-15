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

package batect.ui.containerio

import batect.config.Container
import batect.os.ConsoleInfo
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.testutils.runNullableForEachTest
import com.hypirion.io.RevivableInputStream
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.PrintStream

object TaskContainerOnlyIOStreamingOptionsSpec : Spek({
    describe("a set of I/O streaming options that streams just the task container") {
        val taskContainer by createForEachTest { Container("task-container", imageSourceDoesNotMatter()) }
        val stdout by createForEachTest { mock<PrintStream>() }
        val stdin by createForEachTest { mock<RevivableInputStream>() }
        val consoleInfo by createForEachTest {
            mock<ConsoleInfo> {
                on { terminalType } doReturn "my-terminal"
            }
        }

        val options by createForEachTest { TaskContainerOnlyIOStreamingOptions(taskContainer, stdout, stdin, consoleInfo) }

        given("the current container is the task container") {
            it("returns the current console's terminal type") {
                assertThat(options.terminalTypeForContainer(taskContainer), equalTo("my-terminal"))
            }

            it("returns the system's stdout stream as the stream for the container") {
                assertThat(options.stdoutForContainer(taskContainer), equalTo(UncloseableSink(stdout)))
            }

            on("getting the stdin source for the container") {
                val source by runNullableForEachTest { options.stdinForContainer(taskContainer) }

                it("returns the system's stdin stream") {
                    assertThat(source, equalTo(RevivableSource(stdin)))
                }

                it("resurrects the stream before returning it") {
                    verify(stdin).resurrect()
                }
            }

            it("indicates that stdin should be attached to the container") {
                assertThat(options.attachStdinForContainer(taskContainer), equalTo(true))
            }

            given("stdout is a TTY") {
                beforeEachTest {
                    whenever(consoleInfo.stdoutIsTTY).doReturn(true)
                }

                it("indicates that a TTY should be used for the container") {
                    assertThat(options.useTTYForContainer(taskContainer), equalTo(true))
                }
            }

            given("stdout is not a TTY") {
                beforeEachTest {
                    whenever(consoleInfo.stdoutIsTTY).doReturn(false)
                }

                it("indicates that a TTY should not be used for the container") {
                    assertThat(options.useTTYForContainer(taskContainer), equalTo(false))
                }
            }
        }

        given("the current container is not the task container") {
            beforeEachTest {
                whenever(consoleInfo.stdoutIsTTY).doReturn(true)
            }

            val container by createForEachTest { Container("other-container", imageSourceDoesNotMatter()) }

            it("returns the current console's terminal type") {
                assertThat(options.terminalTypeForContainer(container), equalTo("my-terminal"))
            }

            it("does not return a stdout stream for the container") {
                assertThat(options.stdoutForContainer(container), absent())
            }

            on("getting the stdin source for the container") {
                val source by runNullableForEachTest { options.stdinForContainer(container) }

                it("does not return a stream") {
                    assertThat(source, absent())
                }

                it("does not resurrect the stdin stream") {
                    verify(stdin, never()).resurrect()
                }
            }

            it("indicates that a TTY should not be used for the container") {
                assertThat(options.useTTYForContainer(container), equalTo(false))
            }

            it("indicates that stdin should not be attached to the container") {
                assertThat(options.attachStdinForContainer(container), equalTo(false))
            }
        }
    }
})
