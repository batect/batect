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

package batect.ui

import batect.cli.CommandLineOptionsParser
import batect.config.Container
import batect.docker.DockerContainer
import batect.execution.CleanupOption
import batect.execution.PostTaskManualCleanup
import batect.execution.RunOptions
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerCreationFailedEvent
import batect.execution.model.events.ContainerDidNotBecomeHealthyEvent
import batect.execution.model.events.ContainerRemovalFailedEvent
import batect.execution.model.events.ContainerRunFailedEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.ContainerStopFailedEvent
import batect.execution.model.events.ContainerStoppedEvent
import batect.execution.model.events.CustomTaskNetworkCheckFailedEvent
import batect.execution.model.events.ExecutionFailedEvent
import batect.execution.model.events.ImageBuildFailedEvent
import batect.execution.model.events.ImagePullFailedEvent
import batect.execution.model.events.RunningContainerExitedEvent
import batect.execution.model.events.SetupCommandExecutionErrorEvent
import batect.execution.model.events.SetupCommandFailedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskFailedEvent
import batect.execution.model.events.TaskNetworkCreationFailedEvent
import batect.execution.model.events.TaskNetworkDeletionFailedEvent
import batect.execution.model.events.UserInterruptedExecutionEvent
import batect.os.SystemInfo
import batect.ui.text.Text
import batect.ui.text.TextRun
import batect.ui.text.join

class FailureErrorMessageFormatter(private val runOptions: RunOptions, systemInfo: SystemInfo) {
    private val newLine = systemInfo.lineSeparator

    fun formatErrorMessage(event: TaskFailedEvent): TextRun = when (event) {
        is TaskNetworkCreationFailedEvent -> formatErrorMessage("Could not create network for task", event.message)
        is CustomTaskNetworkCheckFailedEvent -> formatErrorMessage(Text("Could not check details of network ") + Text.bold(event.networkIdentifier), event.message)
        is ImageBuildFailedEvent -> formatErrorMessage(Text("Could not build image for container ") + Text.bold(event.container.name), event.message)
        is ImagePullFailedEvent -> formatErrorMessage(Text("Could not pull image ") + Text.bold(event.source.imageName), event.message)
        is ContainerCreationFailedEvent -> formatErrorMessage(Text("Could not create container ") + Text.bold(event.container.name), event.message)
        is ContainerDidNotBecomeHealthyEvent -> formatErrorMessage(Text("Container ") + Text.bold(event.container.name) + Text(" did not become healthy"), event.message) + hintToReRunWithCleanupDisabled
        is ContainerRunFailedEvent -> formatErrorMessage(Text("Could not run container ") + Text.bold(event.container.name), event.message)
        is ContainerStopFailedEvent -> formatErrorMessage(Text("Could not stop container ") + Text.bold(event.container.name), event.message)
        is ContainerRemovalFailedEvent -> formatErrorMessage(Text("Could not remove container ") + Text.bold(event.container.name), event.message)
        is TaskNetworkDeletionFailedEvent -> formatErrorMessage("Could not delete the task network", event.message)
        is SetupCommandExecutionErrorEvent -> formatErrorMessage(Text("Could not run setup command ") + Text.bold(event.command.command.originalCommand) + Text(" in container ") + Text.bold(event.container.name), event.message) + hintToReRunWithCleanupDisabled
        is SetupCommandFailedEvent -> formatErrorMessage(Text("Setup command ") + Text.bold(event.command.command.originalCommand) + Text(" in container ") + Text.bold(event.container.name) + Text(" failed"), setupCommandFailedBodyText(event.exitCode, event.output)) + hintToReRunWithCleanupDisabled
        is ExecutionFailedEvent -> formatErrorMessage("An unexpected exception occurred during execution", event.message)
        is UserInterruptedExecutionEvent -> formatMessage("Task cancelled", TextRun("Interrupt received during execution"), "Waiting for outstanding operations to stop or finish before cleaning up...")
    }

    private fun formatErrorMessage(headline: String, body: String) = formatErrorMessage(TextRun(headline), body)
    private fun formatErrorMessage(headline: TextRun, body: String) = formatMessage("Error", headline, body)
    private fun formatMessage(type: String, headline: TextRun, body: String) = Text.red(Text.bold("$type: ") + headline + Text(".$newLine")) + Text(body)

    private val hintToReRunWithCleanupDisabled: TextRun by lazy {
        when (runOptions.behaviourAfterFailure) {
            CleanupOption.Cleanup -> Text("$newLine${newLine}You can re-run the task with ") + Text.bold("--${CommandLineOptionsParser.disableCleanupAfterFailureFlagName}") + Text(" to leave the created containers running to diagnose the issue.")
            CleanupOption.DontCleanup -> TextRun("")
        }
    }

    private fun setupCommandFailedBodyText(exitCode: Int, output: String): String = if (output.isEmpty()) {
        "The command exited with code $exitCode and did not produce any output."
    } else {
        "The command exited with code $exitCode and output:$newLine$output"
    }

    fun formatManualCleanupMessage(postTaskManualCleanup: PostTaskManualCleanup.Required, events: Set<TaskEvent>): TextRun {
        return when (postTaskManualCleanup) {
            is PostTaskManualCleanup.Required.DueToCleanupFailure -> formatManualCleanupMessageAfterCleanupFailure(postTaskManualCleanup.manualCleanupCommands)
            is PostTaskManualCleanup.Required.DueToTaskFailureWithCleanupDisabled -> formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(postTaskManualCleanup.manualCleanupCommands, events)
            is PostTaskManualCleanup.Required.DueToTaskSuccessWithCleanupDisabled -> formatManualCleanupMessageAfterTaskSuccessWithCleanupDisabled(postTaskManualCleanup.manualCleanupCommands, events)
        }
    }

    private fun formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(cleanupCommands: List<String>, events: Set<TaskEvent>): TextRun {
        return formatManualCleanupMessageWhenCleanupDisabled(cleanupCommands, events, CommandLineOptionsParser.disableCleanupAfterFailureFlagName, "Once you have finished investigating the issue")
    }

    private fun formatManualCleanupMessageAfterTaskSuccessWithCleanupDisabled(cleanupCommands: List<String>, events: Set<TaskEvent>): TextRun {
        return formatManualCleanupMessageWhenCleanupDisabled(cleanupCommands, events, CommandLineOptionsParser.disableCleanupAfterSuccessFlagName, "Once you have finished using the containers")
    }

    private fun formatManualCleanupMessageWhenCleanupDisabled(cleanupCommands: List<String>, events: Set<TaskEvent>, argumentName: String, cleanupPhrase: String): TextRun {
        val containerCreationEvents = events.filterIsInstance<ContainerCreatedEvent>()

        if (containerCreationEvents.isEmpty()) {
            throw IllegalArgumentException("No containers were created and so this method should not be called.")
        }

        if (cleanupCommands.isEmpty()) {
            throw IllegalArgumentException("No cleanup commands were provided.")
        }

        val containerMessages = containerCreationEvents
            .sortedBy { it.container.name }
            .map { event -> containerOutputAndExecInstructions(event.container, event.dockerContainer, events) }
            .join()

        val formattedCommands = cleanupCommands.joinToString(newLine)

        return Text.red(Text("As the task was run with ") + Text.bold("--$argumentName") + Text(" or ") + Text.bold("--${CommandLineOptionsParser.disableCleanupFlagName}") + Text(", the created containers will not be cleaned up.$newLine")) +
            containerMessages +
            Text(newLine) +
            Text("$cleanupPhrase, clean up all temporary resources created by Batect by running:$newLine") +
            Text.bold(formattedCommands)
    }

    private fun containerOutputAndExecInstructions(container: Container, dockerContainer: DockerContainer, events: Set<TaskEvent>): TextRun {
        val neverStarted = events.none { it is ContainerStartedEvent && it.container == container }
        val exited = events.any { it is RunningContainerExitedEvent && it.container == container }
        val stopped = events.any { it is ContainerStoppedEvent && it.container == container }
        val containerName = dockerContainer.name!!

        val execCommand = if (neverStarted || exited || stopped) {
            "docker start $containerName; docker exec -it $containerName <command>"
        } else {
            "docker exec -it $containerName <command>"
        }

        return Text("For container ") + Text.bold(container.name) + Text(", view its output by running '") + Text.bold("docker logs $containerName") + Text("', or run a command in the container with '") + Text.bold(execCommand) + Text("'.$newLine")
    }

    private fun formatManualCleanupMessageAfterCleanupFailure(cleanupCommands: List<String>): TextRun {
        if (cleanupCommands.isEmpty()) {
            return TextRun()
        }

        val instruction = if (cleanupCommands.size == 1) {
            "You may need to run the following command to clean up any remaining resources:"
        } else {
            "You may need to run some or all of the following commands to clean up any remaining resources:"
        }

        val formattedCommands = cleanupCommands.joinToString(newLine)

        return Text.red("Clean up has failed, and Batect cannot guarantee that all temporary resources created have been completely cleaned up.$newLine") +
            Text(instruction) + Text(newLine) +
            Text.bold(formattedCommands)
    }
}
