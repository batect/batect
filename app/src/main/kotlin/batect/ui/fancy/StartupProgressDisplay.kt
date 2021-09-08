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

import batect.execution.model.events.TaskEvent
import batect.os.ConsoleDimensions
import batect.os.Dimensions
import batect.ui.Console
import batect.ui.text.TextRun

class StartupProgressDisplay(
    val containerLines: List<ContainerStartupProgressLine>,
    val consoleDimensions: ConsoleDimensions
) {
    private var lastStatus = mutableListOf<TextRun>()
    private var lastConsoleDimensions: Dimensions? = null

    fun onEventPosted(event: TaskEvent) {
        containerLines.forEach { it.onEventPosted(event) }
    }

    fun print(console: Console) {
        val newStatus = containerLines.mapTo(mutableListOf()) { it.print() }
        val newConsoleDimensions = consoleDimensions.current

        when {
            lastStatus.isEmpty() -> printAllLines(newStatus, console)
            newConsoleDimensions != lastConsoleDimensions -> reprintAllLines(newStatus, console)
            else -> printOnlyUpdatedLines(newStatus, console)
        }

        lastStatus = newStatus
        lastConsoleDimensions = newConsoleDimensions
    }

    private fun printAllLines(lines: List<TextRun>, console: Console) {
        lines.forEach { console.printLineLimitedToConsoleWidth(it) }
    }

    private fun reprintAllLines(lines: List<TextRun>, console: Console) {
        console.moveCursorUp(lines.size)

        lines.forEach {
            console.clearCurrentLine()
            console.printLineLimitedToConsoleWidth(it)
        }
    }

    private fun printOnlyUpdatedLines(newStatus: List<TextRun>, console: Console) {
        val firstLineWithChange = newStatus.zip(lastStatus).indexOfFirst { (new, old) -> new != old }

        if (firstLineWithChange == -1) {
            return
        }

        console.moveCursorUp(newStatus.size - firstLineWithChange)

        for (i in firstLineWithChange until newStatus.size) {
            val new = newStatus[i]
            val old = lastStatus[i]

            if (new == old) {
                console.moveCursorDown()
            } else {
                console.clearCurrentLine()
                console.printLineLimitedToConsoleWidth(new)
            }
        }
    }
}
