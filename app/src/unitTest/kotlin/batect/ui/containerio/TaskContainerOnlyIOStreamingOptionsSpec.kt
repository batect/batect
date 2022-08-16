/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.ui.containerio

import batect.config.Container
import batect.dockerclient.io.TextInput
import batect.dockerclient.io.TextOutput
import batect.os.ConsoleInfo
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.testutils.runNullableForEachTest
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.PrintStream

object TaskContainerOnlyIOStreamingOptionsSpec : Spek({
    describe("a set of I/O streaming options that streams just the task container") {
        val taskContainer by createForEachTest { Container("task-container", imageSourceDoesNotMatter()) }
        val stdout by createForEachTest { mock<PrintStream>() }
        val consoleInfo by createForEachTest {
            mock<ConsoleInfo> {
                on { terminalType } doReturn "my-terminal"
            }
        }

        val options by createForEachTest { TaskContainerOnlyIOStreamingOptions(taskContainer, stdout, consoleInfo) }

        given("the current container is the task container") {
            it("returns the current console's terminal type") {
                assertThat(options.terminalTypeForContainer(taskContainer), equalTo("my-terminal"))
            }

            it("returns the system's stdout stream as the stream for the container") {
                assertThat(options.stdoutForContainer(taskContainer), equalTo(TextOutput.StandardOutput))
            }

            on("getting the stdin source for the container") {
                val source by runNullableForEachTest { options.stdinForContainer(taskContainer) }

                it("returns the system's stdin stream") {
                    assertThat(source, equalTo(TextInput.StandardInput))
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

            it("does not return a stdin stream for the container") {
                assertThat(options.stdinForContainer(container), absent())
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
