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

package batect.ui.fancy

import batect.config.Container
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerRemovedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.events.TaskNetworkDeletedEvent
import batect.execution.model.events.TemporaryDirectoryCreatedEvent
import batect.execution.model.events.TemporaryDirectoryDeletedEvent
import batect.execution.model.events.TemporaryFileCreatedEvent
import batect.execution.model.events.TemporaryFileDeletedEvent
import batect.ui.text.Text
import batect.ui.text.TextRun
import java.nio.file.Path

class CleanupProgressDisplayLine {
    private var networkHasBeenCreated = false
    private var networkHasBeenDeleted = false
    private val containersCreated = mutableSetOf<Container>()
    private val containersRemoved = mutableSetOf<Container>()
    private val temporaryFilesCreated = mutableSetOf<Path>()
    private val temporaryFilesDeleted = mutableSetOf<Path>()
    private val temporaryDirectoriesCreated = mutableSetOf<Path>()
    private val temporaryDirectoriesDeleted = mutableSetOf<Path>()

    fun onEventPosted(event: TaskEvent) {
        when (event) {
            is ContainerCreatedEvent -> containersCreated.add(event.container)
            is ContainerRemovedEvent -> containersRemoved.add(event.container)
            is TaskNetworkCreatedEvent -> networkHasBeenCreated = true
            is TaskNetworkDeletedEvent -> networkHasBeenDeleted = true
            is TemporaryFileCreatedEvent -> temporaryFilesCreated.add(event.filePath)
            is TemporaryFileDeletedEvent -> temporaryFilesDeleted.add(event.filePath)
            is TemporaryDirectoryCreatedEvent -> temporaryDirectoriesCreated.add(event.directoryPath)
            is TemporaryDirectoryDeletedEvent -> temporaryDirectoriesDeleted.add(event.directoryPath)
        }
    }

    fun print(): TextRun {
        val text = when {
            containersStillToCleanUp.isNotEmpty() -> printContainerCleanupStatus()
            stillNeedToCleanUpNetwork || filesStillToCleanUp > 0 || directoriesStillToCleanUp > 0 -> printOtherCleanupStatus()
            else -> TextRun("Clean up: done")
        }

        return Text.white(text)
    }

    private val containersStillToCleanUp: Set<Container>
        get() = containersCreated - containersRemoved

    private val filesStillToCleanUp: Int
        get() = (temporaryFilesCreated - temporaryFilesDeleted).size

    private val directoriesStillToCleanUp: Int
        get() = (temporaryDirectoriesCreated - temporaryDirectoriesDeleted).size

    private val stillNeedToCleanUpNetwork: Boolean
        get() = networkHasBeenCreated && !networkHasBeenDeleted

    private fun printContainerCleanupStatus(): TextRun {
        val containers = containersStillToCleanUp.map { it.name }.sorted().map { Text.bold(it) }

        return Text("Cleaning up: ${pluralize(containers.size, "container")} (") + humanReadableList(containers) + Text(") left to remove...")
    }

    private fun printOtherCleanupStatus(): TextRun {
        val steps = mutableListOf<Text>()

        if (stillNeedToCleanUpNetwork) {
            steps.add(Text("task network"))
        }

        if (filesStillToCleanUp > 0) {
            steps.add(Text(pluralize(filesStillToCleanUp, "temporary file")))
        }

        if (directoriesStillToCleanUp > 0) {
            steps.add(Text(pluralize(directoriesStillToCleanUp, "temporary directory", "temporary directories")))
        }

        return Text("Cleaning up: removing ") + humanReadableList(steps) + Text("...")
    }
}
