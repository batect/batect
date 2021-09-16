/*
    Copyright 2017-2021 Charles Korn.

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

package batect.ui.fancy

import batect.docker.DockerNetwork
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.os.ConsoleDimensions
import batect.os.Dimensions
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.on
import batect.ui.Console
import batect.ui.text.TextRun
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyZeroInteractions
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object StartupProgressDisplaySpec : Spek({
    describe("a startup progress display") {
        val line1Text = TextRun("The first line's text")
        val line1 by createForEachTest {
            mock<ContainerStartupProgressLine> {
                on { print() } doReturn line1Text
            }
        }

        val line2Text = TextRun("The second line's text")
        val line2 by createForEachTest {
            mock<ContainerStartupProgressLine> {
                on { print() } doReturn line2Text
            }
        }

        val consoleDimensions by createForEachTest {
            mock<ConsoleDimensions> {
                on { current } doReturn Dimensions(123, 456)
            }
        }

        val display by createForEachTest { StartupProgressDisplay(listOf(line1, line2), consoleDimensions) }

        on("receiving an event") {
            val event = TaskNetworkCreatedEvent(DockerNetwork("some-id"))
            beforeEachTest { display.onEventPosted(event) }

            it("forwards it to each progress line") {
                verify(line1).onEventPosted(event)
                verify(line2).onEventPosted(event)
            }
        }

        describe("displaying the current progress") {
            val console by createForEachTest { mock<Console>() }

            given("the progress has never been displayed before") {
                beforeEachTest { display.print(console) }

                it("just prints each progress line, limited to the width of the console") {
                    inOrder(console) {
                        verify(console).printLineLimitedToConsoleWidth(line1Text)
                        verify(console).printLineLimitedToConsoleWidth(line2Text)
                    }
                }
            }

            given("the progress has been displayed before") {
                beforeEachTest {
                    display.print(mock())
                }

                given("the progress lines haven't changed since the last time the progress was displayed") {
                    beforeEachTest {
                        display.print(console)
                    }

                    it("does not print anything") {
                        verifyZeroInteractions(console)
                    }
                }

                given("the console's dimensions have changed since the last time the progress was displayed") {
                    beforeEachTest {
                        whenever(consoleDimensions.current).doReturn(Dimensions(789, 1234))

                        display.print(console)
                    }

                    it("reprints each line") {
                        inOrder(console) {
                            verify(console).moveCursorUp(2)

                            verify(console).clearCurrentLine()
                            verify(console).printLineLimitedToConsoleWidth(line1Text)

                            verify(console).clearCurrentLine()
                            verify(console).printLineLimitedToConsoleWidth(line2Text)
                        }
                    }
                }

                given("only the first line's text has changed") {
                    beforeEachTest {
                        whenever(line1.print()).doReturn(TextRun("Some updated text for the first line"))

                        display.print(console)
                    }

                    it("reprints just the first line") {
                        inOrder(console) {
                            verify(console).moveCursorUp(2)

                            verify(console).clearCurrentLine()
                            verify(console).printLineLimitedToConsoleWidth(TextRun("Some updated text for the first line"))

                            verify(console).moveCursorDown()
                        }
                    }
                }

                given("only the second line's text has changed") {
                    beforeEachTest {
                        whenever(line2.print()).doReturn(TextRun("Some updated text for the second line"))

                        display.print(console)
                    }

                    it("reprints the second line") {
                        inOrder(console) {
                            verify(console).moveCursorUp(1)

                            verify(console).clearCurrentLine()
                            verify(console).printLineLimitedToConsoleWidth(TextRun("Some updated text for the second line"))
                        }
                    }
                }

                given("both lines' text has changed") {
                    beforeEachTest {
                        whenever(line1.print()).doReturn(TextRun("Some updated text for the first line"))
                        whenever(line2.print()).doReturn(TextRun("Some updated text for the second line"))

                        display.print(console)
                    }

                    it("reprints each line") {
                        inOrder(console) {
                            verify(console).moveCursorUp(2)

                            verify(console).clearCurrentLine()
                            verify(console).printLineLimitedToConsoleWidth(TextRun("Some updated text for the first line"))

                            verify(console).clearCurrentLine()
                            verify(console).printLineLimitedToConsoleWidth(TextRun("Some updated text for the second line"))
                        }
                    }
                }
            }
        }
    }
})
