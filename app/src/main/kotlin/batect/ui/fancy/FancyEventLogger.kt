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

import batect.model.events.RunningContainerExitedEvent
import batect.model.events.TaskEvent
import batect.model.steps.DisplayTaskFailureStep
import batect.model.steps.RunContainerStep
import batect.model.steps.TaskStep
import batect.ui.Console
import batect.ui.ConsoleColor
import batect.ui.EventLogger

class FancyEventLogger(
    val console: Console,
    val errorConsole: Console,
    val startupProgressDisplay: StartupProgressDisplay,
    val cleanupProgressDisplay: CleanupProgressDisplay
) : EventLogger(console, errorConsole) {
    private val lock = Object()
    private var keepUpdatingStartupProgress = true
    private var haveStartedCleanup = false

    override fun onStartingTaskStep(step: TaskStep) {
        synchronized(lock) {
            if (step is DisplayTaskFailureStep) {
                keepUpdatingStartupProgress = false
                displayTaskFailure(step)
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

    private fun displayTaskFailure(step: DisplayTaskFailureStep) {
        if (haveStartedCleanup) {
            cleanupProgressDisplay.clear(console)
        } else {
            console.println()
        }

        errorConsole.withColor(ConsoleColor.Red) {
            println(step.message)
            println()
        }

        cleanupProgressDisplay.print(console)
        haveStartedCleanup = true
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
}
