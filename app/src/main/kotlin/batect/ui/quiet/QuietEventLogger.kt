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

package batect.ui.quiet

import batect.model.events.TaskEvent
import batect.model.steps.DisplayTaskFailureStep
import batect.model.steps.TaskStep
import batect.ui.Console
import batect.ui.ConsoleColor
import batect.ui.EventLogger

class QuietEventLogger(val errorConsole: Console) : EventLogger() {
    override fun onStartingTaskStep(step: TaskStep) {
        if (step is DisplayTaskFailureStep) {
            errorConsole.withColor(ConsoleColor.Red) {
                println()
                println(step.message)
            }
        }
    }

    override fun postEvent(event: TaskEvent) {}
    override fun onTaskFailed(taskName: String) {}
    override fun onTaskStarting(taskName: String) {}
}
