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

import batect.execution.model.events.TaskEvent
import batect.testutils.createForEachTest
import batect.ui.Console
import batect.ui.text.TextRun
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object CleanupProgressDisplaySpec : Spek({
    describe("a cleanup progress display") {
        val console by createForEachTest { mock<Console>() }
        val lineText = TextRun("This is the cleanup progress")

        val line by createForEachTest {
            mock<CleanupProgressDisplayLine> {
                on { print() } doReturn lineText
            }
        }

        val cleanupDisplay by createForEachTest { CleanupProgressDisplay(line) }

        on("printing progress information") {
            cleanupDisplay.print(console)

            it("prints the information from the progress line") {
                verify(console).printLineLimitedToConsoleWidth(lineText)
            }
        }

        on("clearing progress previously printed to the console") {
            cleanupDisplay.clear(console)

            it("moves back up to the text previously printed and clears that line") {
                inOrder(console) {
                    verify(console).moveCursorUp()
                    verify(console).clearCurrentLine()
                }
            }
        }

        on("receiving an event") {
            val event = mock<TaskEvent>()

            cleanupDisplay.onEventPosted(event)

            it("passes the event to the progress line") {
                verify(line).onEventPosted(event)
            }
        }
    }
})
