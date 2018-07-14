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

package batect.ui.simple

import batect.config.BuildImage
import batect.config.Container
import batect.execution.RunOptions
import batect.execution.model.events.ContainerBecameHealthyEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.ImageBuiltEvent
import batect.execution.model.events.ImagePulledEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskFailedEvent
import batect.execution.model.steps.BuildImageStep
import batect.execution.model.steps.CleanupStep
import batect.execution.model.steps.CreateContainerStep
import batect.execution.model.steps.PullImageStep
import batect.execution.model.steps.RunContainerStep
import batect.execution.model.steps.StartContainerStep
import batect.execution.model.steps.TaskStep
import batect.os.Command
import batect.ui.Console
import batect.ui.ConsoleColor
import batect.ui.EventLogger
import batect.ui.FailureErrorMessageFormatter

class SimpleEventLogger(
    val containers: Set<Container>,
    val failureErrorMessageFormatter: FailureErrorMessageFormatter,
    val runOptions: RunOptions,
    val console: Console,
    val errorConsole: Console
) : EventLogger() {
    private val commands = mutableMapOf<Container, Command?>()
    private var haveStartedCleanUp = false
    private val lock = Object()

    override fun postEvent(event: TaskEvent) {
        synchronized(lock) {
            when (event) {
                is TaskFailedEvent -> logTaskFailure(failureErrorMessageFormatter.formatErrorMessage(event, runOptions))
                is ImageBuiltEvent -> logImageBuilt(event.buildDirectory)
                is ImagePulledEvent -> logImagePulled(event.image.id)
                is ContainerStartedEvent -> logContainerStarted(event.container)
                is ContainerBecameHealthyEvent -> logContainerBecameHealthy(event.container)
            }
        }
    }

    private fun logTaskFailure(message: String) {
        errorConsole.withColor(ConsoleColor.Red) {
            println()
            println(message)
        }
    }

    override fun onStartingTaskStep(step: TaskStep) {
        synchronized(lock) {
            when (step) {
                is BuildImageStep -> logImageBuildStarting(step.buildDirectory)
                is PullImageStep -> logImagePullStarting(step.imageName)
                is StartContainerStep -> logContainerStarting(step.container)
                is RunContainerStep -> logCommandStarting(step.container, commands[step.container])
                is CreateContainerStep -> commands[step.container] = step.command
                is CleanupStep -> logCleanUpStarting()
            }
        }
    }

    private fun logImageBuildStarting(buildDirectory: String) {
        console.withColor(ConsoleColor.White) {
            containers
                .filter { it.imageSource == BuildImage(buildDirectory) }
                .forEach {
                    print("Building ")
                    printBold(it.name)
                    println("...")
                }
        }
    }

    private fun logImageBuilt(buildDirectory: String) {
        console.withColor(ConsoleColor.White) {
            containers
                .filter { it.imageSource == BuildImage(buildDirectory) }
                .forEach {
                    print("Built ")
                    printBold(it.name)
                    println(".")
                }
        }
    }

    private fun logImagePullStarting(imageName: String) {
        console.withColor(ConsoleColor.White) {
            print("Pulling ")
            printBold(imageName)
            println("...")
        }
    }

    private fun logImagePulled(imageName: String) {
        console.withColor(ConsoleColor.White) {
            print("Pulled ")
            printBold(imageName)
            println(".")
        }
    }

    private fun logCommandStarting(container: Container, command: Command?) {
        console.withColor(ConsoleColor.White) {
            print("Running ")

            if (command != null) {
                printBold(command.originalCommand)
                print(" in ")
            }

            printBold(container.name)
            println("...")
        }
    }

    private fun logContainerStarting(container: Container) {
        console.withColor(ConsoleColor.White) {
            print("Starting ")
            printBold(container.name)
            println("...")
        }
    }

    private fun logContainerStarted(container: Container) {
        console.withColor(ConsoleColor.White) {
            print("Started ")
            printBold(container.name)
            println(".")
        }
    }

    private fun logContainerBecameHealthy(container: Container) {
        console.withColor(ConsoleColor.White) {
            printBold(container.name)
            println(" has become healthy.")
        }
    }

    private fun logCleanUpStarting() {
        if (haveStartedCleanUp) {
            return
        }

        console.withColor(ConsoleColor.White) {
            println()
            println("Cleaning up...")
        }

        haveStartedCleanUp = true
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
