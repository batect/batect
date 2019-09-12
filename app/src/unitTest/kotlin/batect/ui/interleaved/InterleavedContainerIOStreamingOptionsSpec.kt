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

package batect.ui.interleaved

import batect.config.Container
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object InterleavedContainerIOStreamingOptionsSpec : Spek({
    describe("a set of I/O streaming options for interleaved output") {
        val container = Container("some-container", imageSourceDoesNotMatter())
        val output by createForEachTest { mock<InterleavedOutput>() }
        val options by createForEachTest { InterleavedContainerIOStreamingOptions(output) }

        on("getting the terminal type to use for a container") {
            it("returns 'dumb'") {
                assertThat(options.terminalTypeForContainer(container), equalTo("dumb"))
            }
        }

        on("getting the stdin stream to use") {
            it("does not return a stdin stream") {
                assertThat(options.stdinForContainer(container), absent())
            }
        }

        on("getting the stdout stream to use") {
            it("returns an interleaved output stream") {
                assertThat(options.stdoutForContainer(container), equalTo(InterleavedContainerOutputSink(container, output)))
            }
        }
    }
})
