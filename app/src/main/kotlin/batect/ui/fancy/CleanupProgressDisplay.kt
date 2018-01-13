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

package batect.ui.fancy

import batect.config.Container
import batect.model.events.ContainerCreatedEvent
import batect.model.events.ContainerRemovedEvent
import batect.model.events.TaskEvent
import batect.model.events.TaskNetworkCreatedEvent
import batect.model.events.TaskNetworkDeletedEvent
import batect.ui.Console
import batect.ui.ConsoleColor

class CleanupProgressDisplay {
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
        }
    }

    fun print(console: Console) {
        console.withColor(ConsoleColor.White) {
            restrictToConsoleWidth {
                when {
                    containersStillToCleanUp().isNotEmpty() -> printContainerCleanupStatus(this)
                    networkHasBeenCreated && !networkHasBeenDeleted -> print("Cleaning up: removing task network...")
                    else -> print("Clean up: done")
                }
            }

            println()
        }
    }

    private fun containersStillToCleanUp(): Set<Container> = containersCreated - containersRemoved

    private fun printContainerCleanupStatus(console: Console) {
        val containers = containersStillToCleanUp().map { it.name }.sorted()

        if (containers.size == 1) {
            console.print("Cleaning up: 1 container (")
            console.printBold(containers.single())
        } else {
            console.print("Cleaning up: ${containers.size} containers (")

            containers.dropLast(2).forEach {
                console.printBold(it)
                console.print(", ")
            }

            console.printBold(containers[containers.lastIndex - 1])
            console.print(" and ")
            console.printBold(containers[containers.lastIndex])
        }

        console.print(") left to remove...")
    }

    fun clear(console: Console) {
        console.moveCursorUp()
        console.clearCurrentLine()
    }
}
