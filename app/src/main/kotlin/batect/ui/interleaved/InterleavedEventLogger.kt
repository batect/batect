/*
   Copyright 2017-2019 Charles Korn.

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
import batect.execution.RunOptions
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
import batect.execution.model.events.TemporaryDirectoryDeletionFailedEvent
import batect.execution.model.events.TemporaryFileDeletionFailedEvent
import batect.execution.model.events.UserInterruptedExecutionEvent
import batect.execution.model.steps.BuildImageStep
import batect.execution.model.steps.CleanupStep
import batect.execution.model.steps.CreateContainerStep
import batect.execution.model.steps.PullImageStep
import batect.execution.model.steps.RunContainerStep
import batect.os.Command
import batect.ui.EventLogger
import batect.ui.FailureErrorMessageFormatter
import batect.ui.containerio.ContainerIOStreamingOptions
import batect.ui.humanise
import batect.ui.text.Text
import batect.ui.text.TextRun
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class InterleavedEventLogger(
    val taskContainer: Container,
    val containers: Set<Container>,
    private val output: InterleavedOutput,
    val failureErrorMessageFormatter: FailureErrorMessageFormatter,
    val runOptions: RunOptions
) : EventLogger {
    private val haveStartedCleanup = AtomicBoolean(false)
    private val commands = ConcurrentHashMap<Container, Command>()

    override fun onTaskStarting(taskName: String) {
        output.printForTask(Text.white(Text("Running ") + Text.bold(taskName) + Text("...")))
    }

    override fun onTaskFinished(taskName: String, exitCode: Int, duration: Duration) {
        output.printForTask(Text.white(Text.bold(taskName) + Text(" finished with exit code $exitCode in ${duration.humanise()}.")))
    }

    override fun onTaskFinishedWithCleanupDisabled(manualCleanupInstructions: TextRun) {
        output.printErrorForTask(manualCleanupInstructions)
    }

    override fun onTaskFailed(taskName: String, manualCleanupInstructions: TextRun) {
        val message = Text.red(Text("The task ") + Text.bold(taskName) + Text(" failed. See above for details."))

        if (manualCleanupInstructions != TextRun()) {
            output.printErrorForTask(manualCleanupInstructions + Text("\n\n") + message)
        } else {
            output.printErrorForTask(message)
        }
    }

    override val ioStreamingOptions: ContainerIOStreamingOptions by lazy { InterleavedContainerIOStreamingOptions(output, containers) }

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
        }
    }

    private fun onContainerBecameHealthyEvent(event: ContainerBecameHealthyEvent) {
        output.printForContainer(event.container, TextRun(Text.white("Container became healthy.")))
    }

    private fun onImageBuilt(event: ImageBuiltEvent) {
        val text = TextRun(Text.white("Image built."))

        containers
            .filter { it.imageSource == event.source }
            .forEach { output.printForContainer(it, text) }
    }

    private fun onImagePulled(event: ImagePulledEvent) {
        val text = Text.white(Text("Pulled ") + Text.bold(event.source.imageName) + Text("."))

        containers
            .filter { it.imageSource == event.source }
            .forEach { output.printForContainer(it, text) }
    }

    private fun onContainerStarted(event: ContainerStartedEvent) {
        if (event.container != taskContainer) {
            output.printForContainer(event.container, TextRun(Text.white("Container started.")))
        }
    }

    private fun onContainerStoppedEvent(event: ContainerStoppedEvent) {
        output.printForContainer(event.container, TextRun(Text.white("Container stopped.")))
    }

    private fun onRunningSetupCommandEvent(event: RunningSetupCommandEvent) {
        output.printForContainer(event.container, Text.white(Text("Running setup command ") + Text.bold(event.command.command.originalCommand) + Text(" (${event.commandIndex + 1} of ${event.container.setupCommands.size})...")))
    }

    private fun onSetupCommandsCompletedEvent(event: SetupCommandsCompletedEvent) {
        output.printForContainer(event.container, TextRun(Text.white("Container has completed all setup commands.")))
    }

    private fun onStepStarting(event: StepStartingEvent) {
        when (event.step) {
            is BuildImageStep -> onBuildImageStepStarting(event.step)
            is PullImageStep -> onPullImageStepStarting(event.step)
            is RunContainerStep -> onRunContainerStepStarting(event.step)
            is CreateContainerStep -> onCreateContainerStepStarting(event.step)
            is CleanupStep -> onCleanupStepStarting()
        }
    }

    private fun onBuildImageStepStarting(step: BuildImageStep) {
        val text = TextRun(Text.white("Building image..."))

        containers
            .filter { it.imageSource == step.source }
            .forEach { output.printForContainer(it, text) }
    }

    private fun onPullImageStepStarting(step: PullImageStep) {
        val text = Text.white(Text("Pulling ") + Text.bold(step.source.imageName) + Text("..."))

        containers
            .filter { it.imageSource == step.source }
            .forEach { output.printForContainer(it, text) }
    }

    private fun onRunContainerStepStarting(step: RunContainerStep) {
        val command = commands.getOrDefault(step.container, null)

        if (command != null) {
            output.printForContainer(step.container, Text.white(Text("Running ") + Text.bold(command.originalCommand) + Text("...")))
        } else {
            output.printForContainer(step.container, TextRun(Text.white("Running...")))
        }
    }

    private fun onCreateContainerStepStarting(step: CreateContainerStep) {
        if (step.config.command != null) {
            commands[step.container] = step.config.command
        }
    }

    private fun onCleanupStepStarting() {
        val haveStartedCleanupAlready = haveStartedCleanup.getAndSet(true)

        if (haveStartedCleanupAlready) {
            return
        }

        output.printForTask(TextRun(Text.white("Cleaning up...")))
    }

    private fun onTaskFailed(event: TaskFailedEvent) {
        when (event) {
            is ImageBuildFailedEvent -> printErrorForContainers(event.source, event)
            is ImagePullFailedEvent -> printErrorForContainers(event.source, event)
            is ContainerCreationFailedEvent -> printErrorForContainer(event.container, event)
            is ContainerDidNotBecomeHealthyEvent -> printErrorForContainer(event.container, event)
            is ContainerRunFailedEvent -> printErrorForContainer(event.container, event)
            is ContainerStopFailedEvent -> printErrorForContainer(event.container, event)
            is ContainerRemovalFailedEvent -> printErrorForContainer(event.container, event)
            is ExecutionFailedEvent -> printErrorForTask(event)
            is TaskNetworkCreationFailedEvent -> printErrorForTask(event)
            is TaskNetworkDeletionFailedEvent -> printErrorForTask(event)
            is TemporaryFileDeletionFailedEvent -> printErrorForTask(event)
            is TemporaryDirectoryDeletionFailedEvent -> printErrorForTask(event)
            is UserInterruptedExecutionEvent -> printErrorForTask(event)
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
        output.printErrorForContainer(container, failureErrorMessageFormatter.formatErrorMessage(event, runOptions))
    }

    private fun printErrorForTask(event: TaskFailedEvent) {
        output.printErrorForTask(failureErrorMessageFormatter.formatErrorMessage(event, runOptions))
    }
}
