/*
    Copyright 2017-2021 Charles Korn.

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

package batect.ui.interleaved

import batect.config.Container
import batect.config.ImageSource
import batect.execution.PostTaskManualCleanup
import batect.execution.model.events.ContainerBecameHealthyEvent
import batect.execution.model.events.ContainerCreationFailedEvent
import batect.execution.model.events.ContainerDidNotBecomeHealthyEvent
import batect.execution.model.events.ContainerRemovalFailedEvent
import batect.execution.model.events.ContainerRunFailedEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.ContainerStopFailedEvent
import batect.execution.model.events.ContainerStoppedEvent
import batect.execution.model.events.ExecutionFailedEvent
import batect.execution.model.events.ImageBuildFailedEvent
import batect.execution.model.events.ImageBuiltEvent
import batect.execution.model.events.ImagePullFailedEvent
import batect.execution.model.events.ImagePulledEvent
import batect.execution.model.events.RunningSetupCommandEvent
import batect.execution.model.events.SetupCommandsCompletedEvent
import batect.execution.model.events.StepStartingEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskFailedEvent
import batect.execution.model.events.TaskNetworkCreationFailedEvent
import batect.execution.model.events.TaskNetworkDeletionFailedEvent
import batect.execution.model.events.UserInterruptedExecutionEvent
import batect.execution.model.steps.BuildImageStep
import batect.execution.model.steps.CleanupStep
import batect.execution.model.steps.PullImageStep
import batect.execution.model.steps.RunContainerStep
import batect.ui.EventLogger
import batect.ui.FailureErrorMessageFormatter
import batect.ui.containerio.ContainerIOStreamingOptions
import batect.ui.humanise
import batect.ui.text.Text
import batect.ui.text.TextRun
import batect.ui.text.join
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

class InterleavedEventLogger(
    val taskContainer: Container,
    val containers: Set<Container>,
    private val output: InterleavedOutput,
    val failureErrorMessageFormatter: FailureErrorMessageFormatter
) : EventLogger {
    private val haveStartedCleanup = AtomicBoolean(false)
    private val batectPrefix = Text.white("Batect | ")

    override fun onTaskStarting(taskName: String) {
        output.printForTask(batectPrefix + Text("Running ") + Text.bold(taskName) + Text("..."))
    }

    override fun onTaskFinished(taskName: String, exitCode: Long, duration: Duration) {
        output.printForTask(batectPrefix + Text.bold(taskName) + Text(" finished with exit code $exitCode in ${duration.humanise()}."))
    }

    override fun onTaskFinishedWithCleanupDisabled(postTaskManualCleanup: PostTaskManualCleanup.Required, allEvents: Set<TaskEvent>) {
        val manualCleanupInstructions = failureErrorMessageFormatter.formatManualCleanupMessage(postTaskManualCleanup, allEvents)

        output.printErrorForTask(manualCleanupInstructions.prefixAllLines())
    }

    override fun onTaskFailed(taskName: String, postTaskManualCleanup: PostTaskManualCleanup, allEvents: Set<TaskEvent>) {
        val message = Text.red(Text("The task ") + Text.bold(taskName) + Text(" failed. See above for details."))

        when (postTaskManualCleanup) {
            is PostTaskManualCleanup.Required -> {
                val manualCleanupInstructions = failureErrorMessageFormatter.formatManualCleanupMessage(postTaskManualCleanup, allEvents)

                if (manualCleanupInstructions == TextRun()) {
                    output.printErrorForTask(message.prefixAllLines())
                } else {
                    output.printErrorForTask((manualCleanupInstructions + Text("\n\n") + message).prefixAllLines())
                }
            }
            is PostTaskManualCleanup.NotRequired -> output.printErrorForTask(message.prefixAllLines())
        }
    }

    override val ioStreamingOptions: ContainerIOStreamingOptions by lazy { InterleavedContainerIOStreamingOptions(output) }

    override fun postEvent(event: TaskEvent) {
        when (event) {
            is ImageBuiltEvent -> onImageBuilt(event)
            is ImagePulledEvent -> onImagePulled(event)
            is ContainerStartedEvent -> onContainerStarted(event)
            is ContainerBecameHealthyEvent -> onContainerBecameHealthyEvent(event)
            is ContainerStoppedEvent -> onContainerStoppedEvent(event)
            is RunningSetupCommandEvent -> onRunningSetupCommandEvent(event)
            is SetupCommandsCompletedEvent -> onSetupCommandsCompletedEvent(event)
            is StepStartingEvent -> onStepStarting(event)
            is TaskFailedEvent -> onTaskFailed(event)
            else -> {}
        }
    }

    private fun onContainerBecameHealthyEvent(event: ContainerBecameHealthyEvent) {
        output.printForContainer(event.container, batectPrefix + Text("Container became healthy."))
    }

    private fun onImageBuilt(event: ImageBuiltEvent) {
        output.printForContainer(event.container, batectPrefix + Text("Image built."))
    }

    private fun onImagePulled(event: ImagePulledEvent) {
        val text = batectPrefix + Text("Pulled ") + Text.bold(event.source.imageName) + Text(".")

        containers
            .filter { it.imageSource == event.source }
            .forEach { output.printForContainer(it, text) }
    }

    private fun onContainerStarted(event: ContainerStartedEvent) {
        if (event.container != taskContainer) {
            output.printForContainer(event.container, batectPrefix + Text("Container started."))
        }
    }

    private fun onContainerStoppedEvent(event: ContainerStoppedEvent) {
        output.printForContainer(event.container, batectPrefix + Text("Container stopped."))
    }

    private fun onRunningSetupCommandEvent(event: RunningSetupCommandEvent) {
        output.printForContainer(event.container, batectPrefix + Text("Running setup command ") + Text.bold(event.command.command.originalCommand) + Text(" (${event.commandIndex + 1} of ${event.container.setupCommands.size})..."))
    }

    private fun onSetupCommandsCompletedEvent(event: SetupCommandsCompletedEvent) {
        output.printForContainer(event.container, batectPrefix + Text("Container has completed all setup commands."))
    }

    private fun onStepStarting(event: StepStartingEvent) {
        when (event.step) {
            is BuildImageStep -> onBuildImageStepStarting(event.step)
            is PullImageStep -> onPullImageStepStarting(event.step)
            is RunContainerStep -> onRunContainerStepStarting(event.step)
            is CleanupStep -> onCleanupStepStarting()
            else -> {}
        }
    }

    private fun onBuildImageStepStarting(step: BuildImageStep) {
        output.printForContainer(step.container, batectPrefix + Text("Building image..."))
    }

    private fun onPullImageStepStarting(step: PullImageStep) {
        val text = batectPrefix + Text("Pulling ") + Text.bold(step.source.imageName) + Text("...")

        containers
            .filter { it.imageSource == step.source }
            .forEach { output.printForContainer(it, text) }
    }

    private fun onRunContainerStepStarting(step: RunContainerStep) {
        val command = step.container.command

        if (command != null) {
            output.printForContainer(step.container, batectPrefix + Text("Running ") + Text.bold(command.originalCommand) + Text("..."))
        } else {
            output.printForContainer(step.container, batectPrefix + Text("Running..."))
        }
    }

    private fun onCleanupStepStarting() {
        val haveStartedCleanupAlready = haveStartedCleanup.getAndSet(true)

        if (haveStartedCleanupAlready) {
            return
        }

        output.printForTask(batectPrefix + Text("Cleaning up..."))
    }

    private fun onTaskFailed(event: TaskFailedEvent) {
        when (event) {
            is ImageBuildFailedEvent -> printErrorForContainer(event.container, event)
            is ImagePullFailedEvent -> printErrorForContainers(event.source, event)
            is ContainerCreationFailedEvent -> printErrorForContainer(event.container, event)
            is ContainerDidNotBecomeHealthyEvent -> printErrorForContainer(event.container, event)
            is ContainerRunFailedEvent -> printErrorForContainer(event.container, event)
            is ContainerStopFailedEvent -> printErrorForContainer(event.container, event)
            is ContainerRemovalFailedEvent -> printErrorForContainer(event.container, event)
            is ExecutionFailedEvent -> printErrorForTask(event)
            is TaskNetworkCreationFailedEvent -> printErrorForTask(event)
            is TaskNetworkDeletionFailedEvent -> printErrorForTask(event)
            is UserInterruptedExecutionEvent -> printErrorForTask(event)
            else -> {}
        }
    }

    private fun printErrorForContainers(imageSource: ImageSource, event: TaskFailedEvent) {
        val containers = containers.filter { it.imageSource == imageSource }

        if (containers.count() == 1) {
            printErrorForContainer(containers.single(), event)
        } else {
            printErrorForTask(event)
        }
    }

    private fun printErrorForContainer(container: Container, event: TaskFailedEvent) {
        output.printErrorForContainer(container, failureErrorMessageFormatter.formatErrorMessage(event).prefixAllLines())
    }

    private fun printErrorForTask(event: TaskFailedEvent) {
        output.printErrorForTask(failureErrorMessageFormatter.formatErrorMessage(event).prefixAllLines())
    }

    private fun TextRun.prefixAllLines(): TextRun = this.lines
        .map { line -> batectPrefix + line }
        .join(TextRun("\n"))
}
