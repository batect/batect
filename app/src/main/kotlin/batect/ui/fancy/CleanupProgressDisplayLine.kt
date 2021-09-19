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

package batect.ui.fancy

import batect.config.Container
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerRemovedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.events.TaskNetworkDeletedEvent
import batect.ui.text.Text
import batect.ui.text.TextRun
import batect.utils.pluralize

class CleanupProgressDisplayLine {
    private var networkHasBeenCreated = false
    private var networkHasBeenDeleted = false
    private val containersCreated = mutableSetOf<Container>()
    private val containersRemoved = mutableSetOf<Container>()

    fun onEventPosted(event: TaskEvent) {
        when (event) {
            is ContainerCreatedEvent -> containersCreated.add(event.container)
            is ContainerRemovedEvent -> containersRemoved.add(event.container)
            is TaskNetworkCreatedEvent -> networkHasBeenCreated = true
            is TaskNetworkDeletedEvent -> networkHasBeenDeleted = true
            else -> {}
        }
    }

    fun print(): TextRun {
        val text = when {
            containersStillToCleanUp.isNotEmpty() -> printContainerCleanupStatus()
            stillNeedToCleanUpNetwork -> printNetworkCleanupStatus()
            else -> TextRun("Clean up: done")
        }

        return Text.white(text)
    }

    private val containersStillToCleanUp: Set<Container>
        get() = containersCreated - containersRemoved

    private val stillNeedToCleanUpNetwork: Boolean
        get() = networkHasBeenCreated && !networkHasBeenDeleted

    private fun printContainerCleanupStatus(): TextRun {
        val containers = containersStillToCleanUp.map { it.name }.sorted().map { Text.bold(it) }

        return Text("Cleaning up: ${pluralize(containers.size, "container")} (") + humanReadableList(containers) + Text(") left to remove...")
    }

    private fun printNetworkCleanupStatus(): TextRun {
        return TextRun("Cleaning up: removing task network...")
    }
}
