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

package batect.ui.containerio

import batect.config.Container
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.InputStream
import java.io.PrintStream

object TaskContainerOnlyIOStreamingOptionsSpec : Spek({
    describe("a set of I/O streaming options that streams just the task container") {
        val taskContainer by createForEachTest { Container("task-container", imageSourceDoesNotMatter()) }
        val stdout by createForEachTest { mock<PrintStream>() }
        val stdin by createForEachTest { mock<InputStream>() }
        val options by createForEachTest { TaskContainerOnlyIOStreamingOptions(taskContainer, stdout, stdin) }

        given("the current container is the task container") {
            it("enables attaching a TTY to the container") {
                assertThat(options.shouldAttachTTY(taskContainer), equalTo(true))
            }

            it("returns the system's stdout stream as the stream for the container") {
                // HACK: This is the only way to verify that we got an Okio sink that wraps the stream we gave it.
                assertThat(options.stdoutForContainer(taskContainer).toString(), equalTo("sink($stdout)"))
            }

            it("returns the system's stdin stream as the stream for the container") {
                // HACK: This is the only way to verify that we got an Okio source that wraps the stream we gave it.
                assertThat(options.stdinForContainer(taskContainer).toString(), equalTo("source($stdin)"))
            }
        }

        given("the current container is not the task container") {
            val container by createForEachTest { Container("other-container", imageSourceDoesNotMatter()) }

            it("disables attaching a TTY to the container") {
                assertThat(options.shouldAttachTTY(container), equalTo(false))
            }

            it("does not return a stdout stream for the container") {
                assertThat(options.stdoutForContainer(container), absent())
            }

            it("does not return a stdin stream for the container") {
                assertThat(options.stdinForContainer(container), absent())
            }
        }
    }
})
