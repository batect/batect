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
import batect.model.events.ContainerBecameHealthyEvent
import batect.model.events.ContainerCreatedEvent
import batect.model.events.ContainerStartedEvent
import batect.model.events.ImageBuiltEvent
import batect.model.events.TaskEvent
import batect.model.events.TaskNetworkCreatedEvent
import batect.model.steps.BuildImageStep
import batect.model.steps.CreateContainerStep
import batect.model.steps.RunContainerStep
import batect.model.steps.StartContainerStep
import batect.model.steps.TaskStep

class ContainerStartupProgressLine(val container: Container) {
    private var isBuilding = false
    private var hasBeenBuilt = false
    private var isCreating = false
    private var hasBeenCreated = false
    private var isStarting = false
    private var hasStarted = false
    private var isHealthy = false
    private var isRunning = false

    private var networkHasBeenCreated = false

    private val healthyContainers = mutableSetOf<String>()

    fun print(console: Console) {
        console.withColor(ConsoleColor.White) {
            printBold(container.name)
            print(": ")

            when {
                isHealthy || isRunning -> print("done")
                hasStarted -> print("container started, waiting for it to become healthy...")
                isStarting -> print("starting container...")
                hasBeenCreated -> printDescriptionWhenWaitingToStart(this)
                isCreating -> print("creating container...")
                hasBeenBuilt && networkHasBeenCreated -> print("image built, ready to create container")
                hasBeenBuilt && !networkHasBeenCreated -> print("image built, waiting for network to be ready...")
                isBuilding -> print("building image...")
                else -> print("ready to build image")
            }
        }
    }

    private fun printDescriptionWhenWaitingToStart(console: Console) {
        val remainingDependencies = container.dependencies - healthyContainers

        if (remainingDependencies.isEmpty()) {
            console.print("ready to start")
            return
        }

        if (remainingDependencies.size == 1) {
            console.print("waiting for dependency ")
        } else {
            console.print("waiting for dependencies ")
        }

        remainingDependencies.forEachIndexed { i, dependencyName ->
            console.printBold(dependencyName)

            val secondLastDependency = i == remainingDependencies.size - 2
            val beforeSecondLastDependency = i < remainingDependencies.size - 2

            if (secondLastDependency) {
                console.print(" and ")
            } else if (beforeSecondLastDependency) {
                console.print(", ")
            }
        }

        console.print(" to be ready...")
    }

    fun onStepStarting(step: TaskStep) {
        when (step) {
            is BuildImageStep -> onBuildImageStepStarting(step)
            is CreateContainerStep -> onCreateContainerStepStarting(step)
            is StartContainerStep -> onStartContainerStepStarting(step)
            is RunContainerStep -> onRunContainerStepStarting(step)
        }
    }

    private fun onBuildImageStepStarting(step: BuildImageStep) {
        if (step.container == container) {
            isBuilding = true
        }
    }

    private fun onCreateContainerStepStarting(step: CreateContainerStep) {
        if (step.container == container) {
            isCreating = true
        }
    }

    private fun onStartContainerStepStarting(step: StartContainerStep) {
        if (step.container == container) {
            isStarting = true
        }
    }

    private fun onRunContainerStepStarting(step: RunContainerStep) {
        if (step.container == container) {
            isRunning = true
        }
    }

    fun onEventPosted(event: TaskEvent) {
        when (event) {
            is ImageBuiltEvent -> onImageBuiltEventPosted(event)
            is TaskNetworkCreatedEvent -> networkHasBeenCreated = true
            is ContainerCreatedEvent -> onContainerCreatedEventPosted(event)
            is ContainerStartedEvent -> onContainerStartedEventPosted(event)
            is ContainerBecameHealthyEvent -> onContainerBecameHealthyEventPosted(event)
        }
    }

    fun onImageBuiltEventPosted(event: ImageBuiltEvent) {
        if (event.container == container) {
            hasBeenBuilt = true
        }
    }

    fun onContainerCreatedEventPosted(event: ContainerCreatedEvent) {
        if (event.container == container) {
            hasBeenCreated = true
        }
    }

    fun onContainerStartedEventPosted(event: ContainerStartedEvent) {
        if (event.container == container) {
            hasStarted = true
        }
    }

    fun onContainerBecameHealthyEventPosted(event: ContainerBecameHealthyEvent) {
        healthyContainers.add(event.container.name)

        if (event.container == container) {
            isHealthy = true
        }
    }
}
