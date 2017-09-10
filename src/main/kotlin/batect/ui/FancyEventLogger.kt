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

package batect.ui

import batect.model.DependencyGraph
import batect.model.events.TaskEvent
import batect.model.steps.CleanUpContainerStep
import batect.model.steps.DisplayTaskFailureStep
import batect.model.steps.RemoveContainerStep
import batect.model.steps.RunContainerStep
import batect.model.steps.TaskStep

class FancyEventLogger(
        private val console: Console,
        private val errorConsole: Console,
        private val startupProgressDisplayProvider: StartupProgressDisplayProvider
) : EventLogger {
    private val lock = Object()
    private var keepUpdatingStartupProgress = true
    private var haveStartedCleanUp = false
    private var startupProgressDisplay: StartupProgressDisplay? = null

    override fun onDependencyGraphCreated(graph: DependencyGraph) {
        startupProgressDisplay = startupProgressDisplayProvider.createForDependencyGraph(graph)
    }

    override fun logBeforeStartingStep(step: TaskStep) {
        synchronized(lock) {
            if (step is DisplayTaskFailureStep) {
                keepUpdatingStartupProgress = false
                displayTaskFailure(step)
                return
            }

            if (step is RemoveContainerStep || step is CleanUpContainerStep) {
                keepUpdatingStartupProgress = false
                logCleanUpStarting()
                return
            }

            if (keepUpdatingStartupProgress) {
                startupProgressDisplay!!.onStepStarting(step)
                startupProgressDisplay!!.print(console)
            }

            if (step is RunContainerStep) {
                console.println()
                keepUpdatingStartupProgress = false
            }
        }
    }

    private fun displayTaskFailure(step: DisplayTaskFailureStep) {
        errorConsole.withColor(ConsoleColor.Red) {
            println()
            println(step.message)
        }
    }

    private fun logCleanUpStarting() {
        if (haveStartedCleanUp) {
            return
        }

        console.withColor(ConsoleColor.White) {
            println("\nCleaning up...")
        }

        haveStartedCleanUp = true
    }

    override fun postEvent(event: TaskEvent) {
        synchronized(lock) {
            if (keepUpdatingStartupProgress) {
                startupProgressDisplay!!.onEventPosted(event)
                startupProgressDisplay!!.print(console)
            }
        }
    }

    override fun logTaskDoesNotExist(taskName: String) {
        errorConsole.withColor(ConsoleColor.Red) {
            print("The task ")
            printBold(taskName)
            println(" does not exist.")
        }
    }

    override fun logTaskFailed(taskName: String) {
        errorConsole.withColor(ConsoleColor.Red) {
            println()
            print("The task ")
            printBold(taskName)
            println(" failed. See above for details.")
        }
    }
}
