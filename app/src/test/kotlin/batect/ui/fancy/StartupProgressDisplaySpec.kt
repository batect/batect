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

import batect.config.Container
import batect.docker.DockerNetwork
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.steps.BuildImageStep
import batect.testutils.createForEachTest
import batect.testutils.imageSourceDoesNotMatter
import batect.ui.Console
import batect.ui.ConsolePrintStatements
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object StartupProgressDisplaySpec : Spek({
    describe("a startup progress display") {
        val line1 by createForEachTest { mock<ContainerStartupProgressLine>() }
        val line2 by createForEachTest { mock<ContainerStartupProgressLine>() }

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
            val step = BuildImageStep("some-project", Container("some-container", imageSourceDoesNotMatter()))
            display.onStepStarting(step)

            it("forwards it to each progress line") {
                verify(line1).onStepStarting(step)
                verify(line2).onStepStarting(step)
            }
        }

        describe("displaying the current progress") {
            val restrictedWidthConsole by createForEachTest { mock<Console>(name = "restrictedWidthConsole") }
            val console by createForEachTest {
                mock<Console>(name = "console") {
                    on { restrictToConsoleWidth(any()) } doAnswer {
                        val printStatements = it.getArgument<ConsolePrintStatements>(0)
                        printStatements(restrictedWidthConsole)
                    }
                }
            }

            on("when the progress has never been displayed before") {
                display.print(console)

                it("just prints each progress line in a restricted width console") {
                    inOrder(line1, line2, console) {
                        verify(line1).print(restrictedWidthConsole)
                        verify(console).println()
                        verify(line2).print(restrictedWidthConsole)
                        verify(console).println()
                    }
                }
            }

            on("when the progress has been displayed before") {
                display.print(mock())
                reset(line1, line2)

                display.print(console)

                it("moves the cursor to the start of the progress block, and then clears each line and prints the corresponding progress line in a restricted width console") {
                    inOrder(console, line1, line2) {
                        verify(console).moveCursorUp(2)

                        verify(console).clearCurrentLine()
                        verify(line1).print(restrictedWidthConsole)
                        verify(console).println()

                        verify(console).clearCurrentLine()
                        verify(line2).print(restrictedWidthConsole)
                        verify(console).println()
                    }
                }
            }
        }
    }
})
