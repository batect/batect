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

import batect.config.Container
import batect.model.DependencyGraph
import batect.model.events.TaskEvent
import batect.model.steps.BuildImageStep
import batect.model.steps.CleanUpContainerStep
import batect.model.steps.CreateContainerStep
import batect.model.steps.DisplayTaskFailureStep
import batect.model.steps.PullImageStep
import batect.model.steps.RemoveContainerStep
import batect.model.steps.RunContainerStep
import batect.model.steps.StartContainerStep
import batect.model.steps.TaskStep

class SimpleEventLogger(private val console: Console, private val errorConsole: Console) : EventLogger(errorConsole) {
    private val commands = mutableMapOf<Container, String?>()
    private var haveStartedCleanUp = false
    private val lock = Object()

    override fun postEvent(event: TaskEvent) {}

    override fun onStartingTaskStep(step: TaskStep) {
        synchronized(lock) {
            when (step) {
                is BuildImageStep -> logImageBuildStarting(step.container)
                is PullImageStep -> logImagePullStarting(step.imageName)
                is StartContainerStep -> logDependencyContainerStarting(step.container)
                is RunContainerStep -> logCommandStarting(step.container, commands[step.container])
                is DisplayTaskFailureStep -> logTaskFailure(step.message)
                is CreateContainerStep -> commands[step.container] = step.command
                is RemoveContainerStep -> logCleanUpStarting()
                is CleanUpContainerStep -> logCleanUpStarting()
            }
        }
    }

    private fun logImageBuildStarting(container: Container) {
        console.withColor(ConsoleColor.White) {
            print("Building ")
            printBold(container.name)
            println("...")
        }
    }

    private fun logImagePullStarting(imageName: String) {
        console.withColor(ConsoleColor.White) {
            print("Pulling ")
            printBold(imageName)
            println("...")
        }
    }

    private fun logCommandStarting(container: Container, command: String?) {
        console.withColor(ConsoleColor.White) {
            print("Running ")

            if (command != null) {
                printBold(command)
                print(" in ")
            }

            printBold(container.name)
            println("...")
        }
    }

    private fun logDependencyContainerStarting(dependency: Container) {
        console.withColor(ConsoleColor.White) {
            print("Starting dependency ")
            printBold(dependency.name)
            println("...")
        }
    }

    private fun logCleanUpStarting() {
        if (haveStartedCleanUp) {
            return
        }

        console.withColor(ConsoleColor.White) {
            println("Cleaning up...")
        }

        haveStartedCleanUp = true
    }

    private fun logTaskFailure(message: String) {
        errorConsole.withColor(ConsoleColor.Red) {
            println()
            println(message)
        }
    }

    override fun onDependencyGraphCreated(graph: DependencyGraph) {}
}
