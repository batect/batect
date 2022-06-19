/*
    Copyright 2017-2022 Charles Korn.

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

import batect.config.Container
import batect.config.PullImage
import batect.config.SetupCommand
import batect.execution.PostTaskManualCleanup
import batect.execution.model.events.ContainerBecameHealthyEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.ImageBuiltEvent
import batect.execution.model.events.ImagePulledEvent
import batect.execution.model.events.RunningSetupCommandEvent
import batect.execution.model.events.SetupCommandsCompletedEvent
import batect.execution.model.events.StepStartingEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskFailedEvent
import batect.execution.model.steps.BuildImageStep
import batect.execution.model.steps.CleanupStep
import batect.execution.model.steps.PullImageStep
import batect.execution.model.steps.RunContainerStep
import batect.execution.model.steps.TaskStep
import batect.ui.Console
import batect.ui.EventLogger
import batect.ui.FailureErrorMessageFormatter
import batect.ui.containerio.TaskContainerOnlyIOStreamingOptions
import batect.ui.humanise
import batect.ui.text.Text
import batect.ui.text.TextRun
import java.time.Duration

class SimpleEventLogger(
    val containers: Set<Container>,
    val taskContainer: Container,
    val failureErrorMessageFormatter: FailureErrorMessageFormatter,
    val console: Console,
    val errorConsole: Console,
    override val ioStreamingOptions: TaskContainerOnlyIOStreamingOptions
) : EventLogger {
    private var haveStartedCleanUp = false
    private val lock = Object()

    override fun postEvent(event: TaskEvent) {
        synchronized(lock) {
            when (event) {
                is TaskFailedEvent -> logTaskFailure(failureErrorMessageFormatter.formatErrorMessage(event))
                is ImageBuiltEvent -> logImageBuilt(event.container)
                is ImagePulledEvent -> logImagePulled(event.source)
                is ContainerStartedEvent -> logContainerStarted(event.container)
                is ContainerBecameHealthyEvent -> logContainerBecameHealthy(event.container)
                is RunningSetupCommandEvent -> logRunningSetupCommand(event.container, event.command, event.commandIndex)
                is SetupCommandsCompletedEvent -> logSetupCommandsCompleted(event.container)
                is StepStartingEvent -> logStepStarting(event.step)
                else -> {}
            }
        }
    }

    private fun logTaskFailure(message: TextRun) {
        errorConsole.println()
        errorConsole.println(message)
    }

    private fun logStepStarting(step: TaskStep) {
        when (step) {
            is BuildImageStep -> logImageBuildStarting(step.container)
            is PullImageStep -> logImagePullStarting(step.source)
            is RunContainerStep -> logContainerRunning(step.container)
            is CleanupStep -> logCleanUpStarting()
            else -> {}
        }
    }

    private fun logImageBuildStarting(container: Container) {
        console.println(Text.white(Text("Building ") + Text.bold(container.name) + Text("...")))
    }

    private fun logImageBuilt(container: Container) {
        console.println(Text.white(Text("Built ") + Text.bold(container.name) + Text(".")))
    }

    private fun logImagePullStarting(source: PullImage) {
        console.println(Text.white(Text("Pulling ") + Text.bold(source.imageName) + Text("...")))
    }

    private fun logImagePulled(source: PullImage) {
        console.println(Text.white(Text("Pulled ") + Text.bold(source.imageName) + Text(".")))
    }

    private fun logContainerRunning(container: Container) {
        if (container == taskContainer) {
            logTaskContainerRunning(container)
        } else {
            logDependencyContainerStarting(container)
        }
    }

    private fun logTaskContainerRunning(container: Container) {
        val commandText = if (container.command != null) {
            Text.bold(container.command.originalCommand) + Text(" in ")
        } else {
            TextRun()
        }

        console.println(Text.white(Text("Running ") + commandText + Text.bold(container.name) + Text("...")))
    }

    private fun logDependencyContainerStarting(container: Container) {
        console.println(Text.white(Text("Starting ") + Text.bold(container.name) + Text("...")))
    }

    private fun logContainerStarted(container: Container) {
        if (container == taskContainer) {
            return
        }

        console.println(Text.white(Text("Started ") + Text.bold(container.name) + Text(".")))
    }

    private fun logContainerBecameHealthy(container: Container) {
        if (container == taskContainer) {
            return
        }

        console.println(Text.white(Text.bold(container.name) + Text(" has become healthy.")))
    }

    private fun logRunningSetupCommand(container: Container, command: SetupCommand, index: Int) {
        if (container == taskContainer) {
            return
        }

        console.println(Text.white(Text("Running setup command ") + Text.bold(command.command.originalCommand) + Text(" (${index + 1} of ${container.setupCommands.size}) in ") + Text.bold(container.name) + Text("...")))
    }

    private fun logSetupCommandsCompleted(container: Container) {
        if (container == taskContainer) {
            return
        }

        console.println(Text.white(Text.bold(container.name) + Text(" has completed all setup commands.")))
    }

    private fun logCleanUpStarting() {
        if (haveStartedCleanUp) {
            return
        }

        console.println()
        console.println(Text.white("Cleaning up..."))

        haveStartedCleanUp = true
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
        console.println(Text.white(Text.bold(taskName) + Text(" finished with exit code $exitCode in ${duration.humanise()}.")))
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
