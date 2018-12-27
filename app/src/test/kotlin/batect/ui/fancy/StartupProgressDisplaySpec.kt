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

package batect.ui.fancy

import batect.docker.DockerNetwork
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.steps.BuildImageStep
import batect.testutils.createForEachTest
import batect.ui.Console
import batect.ui.text.TextRun
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

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

        val display by createForEachTest { StartupProgressDisplay(listOf(line1, line2)) }

        on("receiving an event") {
            val event = TaskNetworkCreatedEvent(DockerNetwork("some-id"))
            display.onEventPosted(event)

            it("forwards it to each progress line") {
                verify(line1).onEventPosted(event)
                verify(line2).onEventPosted(event)
            }
        }

        on("receiving notification that a step is about to start") {
            val step = BuildImageStep("/some-image-dir")
            display.onStepStarting(step)

            it("forwards it to each progress line") {
                verify(line1).onStepStarting(step)
                verify(line2).onStepStarting(step)
            }
        }

        describe("displaying the current progress") {
            val console by createForEachTest { mock<Console>() }

            on("when the progress has never been displayed before") {
                display.print(console)

                it("just prints each progress line, limited to the width of the console") {
                    inOrder(console) {
                        verify(console).printLineLimitedToConsoleWidth(line1Text)
                        verify(console).printLineLimitedToConsoleWidth(line2Text)
                    }
                }
            }

            on("when the progress has been displayed before") {
                display.print(mock())
                display.print(console)

                it("moves the cursor to the start of the progress block, and then clears each line and prints the corresponding progress line in a restricted width console") {
                    inOrder(console) {
                        verify(console).moveCursorUp(2)

                        verify(console).clearCurrentLine()
                        verify(console).printLineLimitedToConsoleWidth(line1Text)

                        verify(console).clearCurrentLine()
                        verify(console).printLineLimitedToConsoleWidth(line2Text)
                    }
                }
            }
        }
    }
})
