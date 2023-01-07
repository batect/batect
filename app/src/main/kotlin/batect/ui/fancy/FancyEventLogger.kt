/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.ui.fancy

import batect.config.Container
import batect.execution.PostTaskManualCleanup
import batect.execution.model.events.RunningContainerExitedEvent
import batect.execution.model.events.StepStartingEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskFailedEvent
import batect.execution.model.steps.CleanupStep
import batect.execution.model.steps.RunContainerStep
import batect.ui.Console
import batect.ui.EventLogger
import batect.ui.FailureErrorMessageFormatter
import batect.ui.containerio.TaskContainerOnlyIOStreamingOptions
import batect.ui.humanise
import batect.ui.text.Text
import java.time.Duration

class FancyEventLogger(
    val failureErrorMessageFormatter: FailureErrorMessageFormatter,
    val console: Console,
    val errorConsole: Console,
    val startupProgressDisplay: StartupProgressDisplay,
    val cleanupProgressDisplay: CleanupProgressDisplay,
    val taskContainer: Container,
    override val ioStreamingOptions: TaskContainerOnlyIOStreamingOptions,
) : EventLogger {
    private val lock = Object()
    private var keepUpdatingStartupProgress = true
    private var haveStartedCleanup = false

    private fun displayCleanupStatus() {
        if (haveStartedCleanup) {
            cleanupProgressDisplay.clear(console)
        } else {
            console.println()
        }

        cleanupProgressDisplay.print(console)
        haveStartedCleanup = true
    }

    override fun postEvent(event: TaskEvent) {
        synchronized(lock) {
            if (event is TaskFailedEvent) {
                keepUpdatingStartupProgress = false
                displayTaskFailure(event)
                return
            }

            if (event is StepStartingEvent && event.step is CleanupStep) {
                displayCleanupStatus()
                keepUpdatingStartupProgress = false
                return
            }

            if (keepUpdatingStartupProgress) {
                startupProgressDisplay.onEventPosted(event)
                startupProgressDisplay.print(console)
            }

            cleanupProgressDisplay.onEventPosted(event)

            if (event is StepStartingEvent && event.step is RunContainerStep && event.step.container == taskContainer) {
                console.println()
                keepUpdatingStartupProgress = false
            }

            if (haveStartedCleanup || (event is RunningContainerExitedEvent && event.container == taskContainer)) {
                displayCleanupStatus()
            }
        }
    }

    private fun displayTaskFailure(event: TaskFailedEvent) {
        if (haveStartedCleanup) {
            cleanupProgressDisplay.clear(console)
        } else {
            console.println()
        }

        errorConsole.println(failureErrorMessageFormatter.formatErrorMessage(event))

        if (haveStartedCleanup) {
            console.println()
            cleanupProgressDisplay.print(console)
        }
    }

    override fun onTaskFailed(taskName: String, postTaskManualCleanup: PostTaskManualCleanup, allEvents: Set<TaskEvent>) {
        when (postTaskManualCleanup) {
            is PostTaskManualCleanup.NotRequired -> {}
            is PostTaskManualCleanup.Required -> printManualCleanupInstructions(postTaskManualCleanup, allEvents)
        }

        errorConsole.println()
        errorConsole.println(Text.red(Text("The task ") + Text.bold(taskName) + Text(" failed. See above for details.")))
    }

    override fun onTaskStarting(taskName: String) {
        console.println(Text.white(Text("Running ") + Text.bold(taskName) + Text("...")))
    }

    override fun onTaskFinished(taskName: String, exitCode: Long, duration: Duration) {
        cleanupProgressDisplay.clear(console)

        val exitCodeText = if (exitCode == 0L) {
            Text.green(exitCode.toString())
        } else {
            Text.red(exitCode.toString())
        }

        console.println(Text.white(Text.bold(taskName) + Text(" finished with exit code ") + Text.bold(exitCodeText) + Text(" in ${duration.humanise()}.")))
    }

    override fun onTaskFinishedWithCleanupDisabled(postTaskManualCleanup: PostTaskManualCleanup.Required, allEvents: Set<TaskEvent>) {
        printManualCleanupInstructions(postTaskManualCleanup, allEvents)
    }

    private fun printManualCleanupInstructions(postTaskManualCleanup: PostTaskManualCleanup.Required, allEvents: Set<TaskEvent>) {
        val manualCleanupInstructions = failureErrorMessageFormatter.formatManualCleanupMessage(postTaskManualCleanup, allEvents) ?: return

        errorConsole.println()
        errorConsole.println(manualCleanupInstructions)
    }
}
