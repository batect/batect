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

package batect.ui.fancy

import batect.config.BuildImage
import batect.config.Container
import batect.config.PullImage
import batect.docker.DockerImageBuildProgress
import batect.docker.pull.DockerImagePullProgress
import batect.execution.model.events.ContainerBecameHealthyEvent
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.ImageBuildProgressEvent
import batect.execution.model.events.ImageBuiltEvent
import batect.execution.model.events.ImagePullProgressEvent
import batect.execution.model.events.ImagePulledEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.steps.BuildImageStep
import batect.execution.model.steps.CreateContainerStep
import batect.execution.model.steps.PullImageStep
import batect.execution.model.steps.RunContainerStep
import batect.execution.model.steps.StartContainerStep
import batect.execution.model.steps.TaskStep
import batect.os.Command
import batect.ui.Console
import batect.ui.ConsoleColor

class ContainerStartupProgressLine(val container: Container, val dependencies: Set<Container>) {
    private var isBuilding = false
    private var lastBuildProgressUpdate: DockerImageBuildProgress? = null
    private var isPulling = false
    private var lastPullProgressUpdate: DockerImagePullProgress? = null
    private var hasBeenBuilt = false
    private var hasBeenPulled = false
    private var isCreating = false
    private var hasBeenCreated = false
    private var isStarting = false
    private var hasStarted = false
    private var isHealthy = false
    private var isRunning = false
    private var command: Command? = null

    private var networkHasBeenCreated = false

    private val healthyContainers = mutableSetOf<Container>()

    fun print(console: Console) {
        console.withColor(ConsoleColor.White) {
            printBold(container.name)
            print(": ")

            when {
                isHealthy -> print("running")
                isRunning -> printDescriptionWhenRunning()
                hasStarted -> print("container started, waiting for it to become healthy...")
                isStarting -> print("starting container...")
                hasBeenCreated -> printDescriptionWhenWaitingToStart()
                isCreating -> print("creating container...")
                hasBeenBuilt && networkHasBeenCreated -> print("image built, ready to create container")
                hasBeenBuilt && !networkHasBeenCreated -> print("image built, waiting for network to be ready...")
                hasBeenPulled && networkHasBeenCreated -> print("image pulled, ready to create container")
                hasBeenPulled && !networkHasBeenCreated -> print("image pulled, waiting for network to be ready...")
                isBuilding && lastBuildProgressUpdate == null -> print("building image...")
                isBuilding && lastBuildProgressUpdate != null -> printDescriptionWhenBuilding()
                isPulling -> printDescriptionWhenPulling()
                else -> printDescriptionWhenWaitingToBuildOrPull()
            }
        }
    }

    private fun Console.printDescriptionWhenWaitingToBuildOrPull() {
        when (container.imageSource) {
            is BuildImage -> print("ready to build image")
            is PullImage -> print("ready to pull image")
        }
    }

    private val containerImageName by lazy { (container.imageSource as PullImage).imageName }

    private fun Console.printDescriptionWhenPulling() {
        print("pulling ")
        printBold(containerImageName)

        if (lastPullProgressUpdate == null) {
            print("...")
        } else {
            print(": " + lastPullProgressUpdate!!.toStringForDisplay())
        }
    }

    private fun Console.printDescriptionWhenBuilding() {
        print("building image: step ${lastBuildProgressUpdate!!.currentStep} of ${lastBuildProgressUpdate!!.totalSteps}: ${lastBuildProgressUpdate!!.message}")

        if (lastBuildProgressUpdate!!.pullProgress != null) {
            print(": ")
            print(lastBuildProgressUpdate!!.pullProgress!!.toStringForDisplay())
        }
    }

    private fun Console.printDescriptionWhenWaitingToStart() {
        val remainingDependencies = dependencies - healthyContainers

        if (remainingDependencies.isEmpty()) {
            print("ready to start")
            return
        }

        if (remainingDependencies.size == 1) {
            print("waiting for dependency ")
        } else {
            print("waiting for dependencies ")
        }

        remainingDependencies.forEachIndexed { i, dependency ->
            printBold(dependency.name)

            val secondLastDependency = i == remainingDependencies.size - 2
            val beforeSecondLastDependency = i < remainingDependencies.size - 2

            if (secondLastDependency) {
                print(" and ")
            } else if (beforeSecondLastDependency) {
                print(", ")
            }
        }

        print(" to be ready...")
    }

    private fun Console.printDescriptionWhenRunning() {
        if (command == null) {
            print("running")
        } else {
            print("running ")
            printBold(command!!.originalCommand)
        }
    }

    fun onStepStarting(step: TaskStep) {
        when (step) {
            is BuildImageStep -> onBuildImageStepStarting(step)
            is PullImageStep -> onPullImageStepStarting(step)
            is CreateContainerStep -> onCreateContainerStepStarting(step)
            is StartContainerStep -> onStartContainerStepStarting(step)
            is RunContainerStep -> onRunContainerStepStarting(step)
        }
    }

    private fun onBuildImageStepStarting(step: BuildImageStep) {
        if (container.imageSource is BuildImage && step.buildDirectory == container.imageSource.buildDirectory) {
            isBuilding = true
        }
    }

    private fun onPullImageStepStarting(step: PullImageStep) {
        if (container.imageSource is PullImage && step.imageName == container.imageSource.imageName) {
            isPulling = true
        }
    }

    private fun onCreateContainerStepStarting(step: CreateContainerStep) {
        if (step.container == container) {
            isCreating = true
            command = step.command
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
            is ImageBuildProgressEvent -> onImageBuildProgressEventPosted(event)
            is ImageBuiltEvent -> onImageBuiltEventPosted(event)
            is ImagePullProgressEvent -> onImagePullProgressEventPosted(event)
            is ImagePulledEvent -> onImagePulledEventPosted(event)
            is TaskNetworkCreatedEvent -> networkHasBeenCreated = true
            is ContainerCreatedEvent -> onContainerCreatedEventPosted(event)
            is ContainerStartedEvent -> onContainerStartedEventPosted(event)
            is ContainerBecameHealthyEvent -> onContainerBecameHealthyEventPosted(event)
        }
    }

    private fun onImageBuildProgressEventPosted(event: ImageBuildProgressEvent) {
        if (container.imageSource == BuildImage(event.buildDirectory)) {
            isBuilding = true
            lastBuildProgressUpdate = event.progress
        }
    }

    private fun onImageBuiltEventPosted(event: ImageBuiltEvent) {
        if (container.imageSource == BuildImage(event.buildDirectory)) {
            hasBeenBuilt = true
        }
    }

    private fun onImagePullProgressEventPosted(event: ImagePullProgressEvent) {
        if (container.imageSource == PullImage(event.imageName)) {
            isPulling = true
            lastPullProgressUpdate = event.progress
        }
    }

    private fun onImagePulledEventPosted(event: ImagePulledEvent) {
        if (container.imageSource == PullImage(event.image.id)) {
            hasBeenPulled = true
        }
    }

    private fun onContainerCreatedEventPosted(event: ContainerCreatedEvent) {
        if (event.container == container) {
            hasBeenCreated = true
        }
    }

    private fun onContainerStartedEventPosted(event: ContainerStartedEvent) {
        if (event.container == container) {
            hasStarted = true
        }
    }

    private fun onContainerBecameHealthyEventPosted(event: ContainerBecameHealthyEvent) {
        healthyContainers.add(event.container)

        if (event.container == container) {
            isHealthy = true
        }
    }
}
