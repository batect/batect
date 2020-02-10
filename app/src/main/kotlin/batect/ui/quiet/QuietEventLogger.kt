/*
   Copyright 2017-2020 Charles Korn.

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

import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskFailedEvent
import batect.ui.Console
import batect.ui.EventLogger
import batect.ui.FailureErrorMessageFormatter
import batect.ui.containerio.TaskContainerOnlyIOStreamingOptions
import batect.ui.text.TextRun
import java.time.Duration

class QuietEventLogger(
    val failureErrorMessageFormatter: FailureErrorMessageFormatter,
    val errorConsole: Console,
    override val ioStreamingOptions: TaskContainerOnlyIOStreamingOptions
) : EventLogger {
    override fun postEvent(event: TaskEvent) {
        if (event is TaskFailedEvent) {
            errorConsole.println()
            errorConsole.println(failureErrorMessageFormatter.formatErrorMessage(event))
        }
    }

    override fun onTaskFailed(taskName: String, manualCleanupInstructions: TextRun) {}
    override fun onTaskStarting(taskName: String) {}
    override fun onTaskFinished(taskName: String, exitCode: Int, duration: Duration) {}
    override fun onTaskFinishedWithCleanupDisabled(manualCleanupInstructions: TextRun) {}
}
