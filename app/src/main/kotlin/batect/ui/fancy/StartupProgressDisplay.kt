/*
   Copyright 2017 Charles Korn.

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

import batect.model.events.TaskEvent
import batect.model.steps.TaskStep
import batect.ui.Console

class StartupProgressDisplay(val containerLines: List<ContainerStartupProgressLine>) {
    private var havePrintedOnceBefore = false

    fun onEventPosted(event: TaskEvent) {
        containerLines.forEach { it.onEventPosted(event) }
    }

    fun onStepStarting(step: TaskStep) {
        containerLines.forEach { it.onStepStarting(step) }
    }

    fun print(console: Console) {
        if (havePrintedOnceBefore) {
            console.moveCursorUp(containerLines.size)
        }

        containerLines.forEach { line ->
            if (havePrintedOnceBefore) {
                console.clearCurrentLine()
            }

            console.restrictToConsoleWidth {
                line.print(this)
            }

            console.println()
        }

        havePrintedOnceBefore = true
    }
}
