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

package batect.ui

import batect.execution.BehaviourAfterFailure
import batect.execution.RunOptions
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerCreationFailedEvent
import batect.execution.model.events.ContainerDidNotBecomeHealthyEvent
import batect.execution.model.events.ContainerRemovalFailedEvent
import batect.execution.model.events.ContainerRunFailedEvent
import batect.execution.model.events.ContainerStartFailedEvent
import batect.execution.model.events.ContainerStopFailedEvent
import batect.execution.model.events.ExecutionFailedEvent
import batect.execution.model.events.ImageBuildFailedEvent
import batect.execution.model.events.ImagePullFailedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskFailedEvent
import batect.execution.model.events.TaskNetworkCreationFailedEvent
import batect.execution.model.events.TaskNetworkDeletionFailedEvent
import batect.execution.model.events.TemporaryDirectoryDeletionFailedEvent
import batect.execution.model.events.TemporaryFileDeletionFailedEvent

class FailureErrorMessageFormatter {
    fun formatErrorMessage(event: TaskFailedEvent, runOptions: RunOptions): String = when (event) {
        is TaskNetworkCreationFailedEvent -> "Could not create network for task: ${event.message}"
        is ImageBuildFailedEvent -> "Could not build image from directory '${event.buildDirectory}': ${event.message}"
        is ImagePullFailedEvent -> "Could not pull image '${event.imageName}': ${event.message}"
        is ContainerCreationFailedEvent -> "Could not create container '${event.container.name}': ${event.message}"
        is ContainerStartFailedEvent -> "Could not start container '${event.container.name}': ${event.message}" + hintToReRunWithCleanupDisabled(runOptions)
        is ContainerDidNotBecomeHealthyEvent -> "Container '${event.container.name}' did not become healthy: ${event.message}" + hintToReRunWithCleanupDisabled(runOptions)
        is ContainerRunFailedEvent -> "Could not run container '${event.container.name}': ${event.message}"
        is ContainerStopFailedEvent -> "Could not stop container '${event.container.name}': ${event.message}"
        is ContainerRemovalFailedEvent -> "Could not remove container '${event.container.name}': ${event.message}"
        is TaskNetworkDeletionFailedEvent -> "Could not delete the task network: ${event.message}"
        is TemporaryFileDeletionFailedEvent -> "Could not delete temporary file '${event.filePath}': ${event.message}"
        is TemporaryDirectoryDeletionFailedEvent -> "Could not delete temporary directory '${event.directoryPath}': ${event.message}"
        is ExecutionFailedEvent -> "An unexpected exception occurred during execution: ${event.message}"
    }

    private fun hintToReRunWithCleanupDisabled(runOptions: RunOptions): String = when (runOptions.behaviourAfterFailure) {
        BehaviourAfterFailure.Cleanup -> "\n\nYou can re-run the task with --no-cleanup-after-failure to leave the created containers running to diagnose the issue."
        BehaviourAfterFailure.DontCleanup -> ""
    }

    fun formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(events: Set<TaskEvent>, cleanupCommands: List<String>): String {
        val containerCreationEvents = events.filterIsInstance<ContainerCreatedEvent>()

        if (containerCreationEvents.isEmpty()) {
            throw IllegalArgumentException("No containers were created and so this method should not be called.")
        }

        if (cleanupCommands.isEmpty()) {
            throw IllegalArgumentException("No cleanup commands were provided.")
        }

        val containerMessages = containerCreationEvents
            .sortedBy { it.container.name }
            .map { event -> "For container '${event.container.name}': view its output by running 'docker logs ${event.dockerContainer.id}', or run a command in the container with 'docker exec -it ${event.dockerContainer.id} <command>'." }
            .joinToString("\n")

        val formattedCommands = cleanupCommands.joinToString("\n")

        return "As the task was run with --no-cleanup-after-failure, the created containers will not be cleaned up.\n" +
            containerMessages +
            "\n" +
            "\n" +
            "Once you have finished investigating the issue, you can clean up all temporary resources created by batect by running:\n" +
            formattedCommands
    }

    fun formatManualCleanupMessageAfterCleanupFailure(cleanupCommands: List<String>): String {
        if (cleanupCommands.isEmpty()) {
            return ""
        }

        val instruction = if (cleanupCommands.size == 1) {
            "You may need to run the following command to clean up any remaining resources:"
        } else {
            "You may need to run some or all of the following commands to clean up any remaining resources:"
        }

        val formattedCommands = cleanupCommands.joinToString("\n")

        return """
                Clean up has failed, and batect cannot guarantee that all temporary resources created have been completely cleaned up.
                $instruction
            """.trimIndent() + "\n\n" + formattedCommands
    }
}
