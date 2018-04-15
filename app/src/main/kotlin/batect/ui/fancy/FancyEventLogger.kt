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

import batect.model.RunOptions
import batect.model.events.RunningContainerExitedEvent
import batect.model.events.TaskEvent
import batect.model.events.TaskFailedEvent
import batect.model.steps.CleanupStep
import batect.model.steps.RunContainerStep
import batect.model.steps.TaskStep
import batect.ui.Console
import batect.ui.ConsoleColor
import batect.ui.EventLogger
import batect.ui.FailureErrorMessageFormatter

class FancyEventLogger(
    val failureErrorMessageFormatter: FailureErrorMessageFormatter,
    val runOptions: RunOptions,
    val console: Console,
    val errorConsole: Console,
    val startupProgressDisplay: StartupProgressDisplay,
    val cleanupProgressDisplay: CleanupProgressDisplay
) : EventLogger() {
    private val lock = Object()
    private var keepUpdatingStartupProgress = true
    private var haveStartedCleanup = false

    override fun onStartingTaskStep(step: TaskStep) {
        synchronized(lock) {
            if (step is CleanupStep) {
                displayCleanupStatus()
                keepUpdatingStartupProgress = false
                return
            }

            if (keepUpdatingStartupProgress) {
                startupProgressDisplay.onStepStarting(step)
                startupProgressDisplay.print(console)
            }

            if (step is RunContainerStep) {
                console.println()
                keepUpdatingStartupProgress = false
            }
        }
    }

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

            if (keepUpdatingStartupProgress) {
                startupProgressDisplay.onEventPosted(event)
                startupProgressDisplay.print(console)
            }

            cleanupProgressDisplay.onEventPosted(event)

            if (haveStartedCleanup || event is RunningContainerExitedEvent) {
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

        errorConsole.withColor(ConsoleColor.Red) {
            println(failureErrorMessageFormatter.formatErrorMessage(event, runOptions))
        }

        if (haveStartedCleanup) {
            console.println()
            cleanupProgressDisplay.print(console)
        }
    }

    override fun onTaskFailed(taskName: String, manualCleanupInstructions: String) {
        errorConsole.withColor(ConsoleColor.Red) {
            if (manualCleanupInstructions != "") {
                println()
                println(manualCleanupInstructions)
            }

            println()
            print("The task ")
            printBold(taskName)
            println(" failed. See above for details.")
        }
    }

    override fun onTaskStarting(taskName: String) {
        console.withColor(ConsoleColor.White) {
            print("Running ")
            printBold(taskName)
            println("...")
        }
    }
}
