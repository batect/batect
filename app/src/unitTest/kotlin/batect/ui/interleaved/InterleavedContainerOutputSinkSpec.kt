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
import batect.testutils.imageSourceDoesNotMatter
import batect.ui.text.TextRun
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import okio.Buffer
import okio.Sink
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object InterleavedContainerOutputSinkSpec : Spek({
    describe("a container output sink for interleaved output") {
        val container = Container("some-container", imageSourceDoesNotMatter())
        val output by createForEachTest { mock<InterleavedOutput>() }
        val sink by createForEachTest { InterleavedContainerOutputSink(container, output) }

        describe("writing output") {
            describe("when zero bytes are written") {
                beforeEachTest { sink.write(Buffer(), 0) }

                it("does not write anything to the output") {
                    verify(output, never()).printForContainer(any(), any())
                }
            }

            describe("when bytes ending a new line are written") {
                beforeEachTest { sink.writeText("Some text\n") }

                it("writes the text to the output without the trailing new line character") {
                    verify(output).printForContainer(container, TextRun("Some text"))
                }
            }

            describe("when bytes containing multiple new lines are written") {
                beforeEachTest { sink.writeText("Line 1\nLine 2\n") }

                it("writes both lines to the output") {
                    inOrder(output) {
                        verify(output).printForContainer(container, TextRun("Line 1"))
                        verify(output).printForContainer(container, TextRun("Line 2"))
                    }
                }
            }

            describe("when bytes containing a new line are written") {
                beforeEachTest { sink.writeText("Line 1\nStart of line 2...") }

                it("writes only the complete lines of text to the output") {
                    verify(output).printForContainer(container, TextRun("Line 1"))
                    verifyNoMoreInteractions(output)
                }
            }

            describe("when bytes not containing a new line are written") {
                beforeEachTest { sink.writeText("Not yet...") }

                it("does not write anything to the output") {
                    verify(output, never()).printForContainer(any(), any())
                }
            }

            describe("when bytes not containing a new line are written and then a new line is written") {
                beforeEachTest {
                    sink.writeText("Not yet...")
                    sink.writeText("now!\n")
                }

                it("writes the text to the output without the trailing new line character") {
                    verify(output).printForContainer(container, TextRun("Not yet...now!"))
                }
            }

            describe("when bytes containing a new line are written and then more bytes containing a new line are written") {
                beforeEachTest {
                    sink.writeText("Line 1\nLine")
                    sink.writeText(" 2\n")
                }

                it("writes both complete lines to the output") {
                    inOrder(output) {
                        verify(output).printForContainer(container, TextRun("Line 1"))
                        verify(output).printForContainer(container, TextRun("Line 2"))
                    }
                }
            }
        }

        describe("closing the sink") {
            describe("when no output has been written") {
                beforeEachTest { sink.close() }

                it("does not write anything to the output") {
                    verify(output, never()).printForContainer(any(), any())
                }
            }

            describe("when no incomplete lines have been written") {
                beforeEachTest {
                    sink.writeText("Line 1\n")
                    reset(output)

                    sink.close()
                }

                it("does not write anything to the output") {
                    verify(output, never()).printForContainer(any(), any())
                }
            }

            describe("when an incomplete line has been written") {
                beforeEachTest {
                    sink.writeText("Wait for it...")
                    sink.close()
                }

                it("writes the remaining text to the output") {
                    verify(output).printForContainer(container, TextRun("Wait for it..."))
                }
            }
        }
    }
})

private fun Sink.writeText(text: String) {
    val bytes = text.toByteArray(Charsets.UTF_8)

    this.write(Buffer().write(bytes), bytes.size.toLong())
}
