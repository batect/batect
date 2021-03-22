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

import batect.config.BuildImage
import batect.config.CacheMount
import batect.config.Container
import batect.config.PullImage
import batect.config.SetupCommand
import batect.docker.build.BuildProgress
import batect.docker.pull.ImagePullProgress
import batect.execution.model.events.CachesInitialisedEvent
import batect.execution.model.events.ContainerBecameHealthyEvent
import batect.execution.model.events.ContainerBecameReadyEvent
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.ContainerStartedEvent
import batect.execution.model.events.ImageBuildProgressEvent
import batect.execution.model.events.ImageBuiltEvent
import batect.execution.model.events.ImagePullProgressEvent
import batect.execution.model.events.ImagePulledEvent
import batect.execution.model.events.RunningSetupCommandEvent
import batect.execution.model.events.StepStartingEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskNetworkReadyEvent
import batect.execution.model.steps.BuildImageStep
import batect.execution.model.steps.CreateContainerStep
import batect.execution.model.steps.PullImageStep
import batect.execution.model.steps.RunContainerStep
import batect.execution.model.steps.TaskStep
import batect.ui.text.Text
import batect.ui.text.TextRun

data class ContainerStartupProgressLine(val container: Container, val dependencies: Set<Container>, val isTaskContainer: Boolean) {
    private var isBuilding = false
    private var lastBuildProgressUpdate: BuildProgress? = null
    private var isPulling = false
    private var lastProgressUpdate: ImagePullProgress? = null
    private var hasBeenBuilt = false
    private var hasBeenPulled = false
    private var isCreating = false
    private var hasBeenCreated = false
    private var isStarting = false
    private var hasStarted = false
    private var isReady = false
    private var isHealthy = false
    private var isRunning = false
    private var networkIsReady = false
    private val needsCacheInitialisation = container.volumeMounts.any { it is CacheMount }
    private var cacheInitialised = false

    private var setupCommandState: SetupCommandState = if (container.setupCommands.isEmpty()) {
        SetupCommandState.None
    } else {
        SetupCommandState.NotStarted
    }

    private val readyContainers = mutableSetOf<Container>()

    fun print(): TextRun {
        val description = when {
            isReady || (isHealthy && setupCommandState == SetupCommandState.None) -> TextRun("running")
            setupCommandState is SetupCommandState.Running -> descriptionWhenRunningSetupCommand()
            isHealthy && setupCommandState == SetupCommandState.NotStarted -> TextRun("running setup commands...")
            isRunning -> descriptionWhenRunning()
            hasStarted -> TextRun("container started, waiting for it to become healthy...")
            isStarting -> TextRun("starting container...")
            hasBeenCreated -> descriptionWhenWaitingToStart()
            isCreating -> TextRun("creating container...")
            (hasBeenPulled || hasBeenBuilt) && networkIsReady && !cacheInitialised && needsCacheInitialisation -> TextRun("waiting for cache initialisation to finish...")
            hasBeenBuilt && networkIsReady && (cacheInitialised || !needsCacheInitialisation) -> TextRun("image built, ready to create container")
            hasBeenBuilt && !networkIsReady -> TextRun("image built, waiting for network to be ready...")
            hasBeenPulled && networkIsReady && (cacheInitialised || !needsCacheInitialisation) -> TextRun("image pulled, ready to create container")
            hasBeenPulled && !networkIsReady -> TextRun("image pulled, waiting for network to be ready...")
            isBuilding && lastBuildProgressUpdate == null -> TextRun("building image...")
            isBuilding && lastBuildProgressUpdate != null -> descriptionWhenBuilding()
            isPulling -> descriptionWhenPulling()
            else -> descriptionWhenWaitingToBuildOrPull()
        }

        return Text.white(Text.bold(container.name) + Text(": ") + description)
    }

    private fun descriptionWhenRunningSetupCommand(): TextRun {
        val state = setupCommandState as SetupCommandState.Running

        return Text("running setup command ") + Text.bold(state.command.command.originalCommand) + Text(" (${state.index + 1} of ${container.setupCommands.size})...")
    }

    private fun descriptionWhenWaitingToBuildOrPull(): TextRun {
        return when (container.imageSource) {
            is BuildImage -> TextRun("ready to build image")
            is PullImage -> TextRun("ready to pull image")
        }
    }

    private val containerImageName by lazy { (container.imageSource as PullImage).imageName }

    private fun descriptionWhenPulling(): TextRun {
        val progressInformation = if (lastProgressUpdate == null) {
            Text("...")
        } else {
            Text(": " + lastProgressUpdate!!.toStringForDisplay())
        }

        return Text("pulling ") + Text.bold(containerImageName) + progressInformation
    }

    private fun descriptionWhenBuilding(): TextRun {
        return TextRun("building image: " + lastBuildProgressUpdate!!.toHumanReadableString())
    }

    private fun descriptionWhenWaitingToStart(): TextRun {
        val remainingDependencies = dependencies - readyContainers

        if (remainingDependencies.isEmpty()) {
            return TextRun("ready to start")
        }

        val noun = if (remainingDependencies.size == 1) {
            "dependency"
        } else {
            "dependencies"
        }

        val formattedDependencyNames = remainingDependencies.map { Text.bold(it.name) }

        return Text("waiting for $noun ") + humanReadableList(formattedDependencyNames) + Text(" to be ready...")
    }

    private fun descriptionWhenRunning(): TextRun {
        if (container.command == null) {
            return TextRun("running")
        }

        return Text("running ") + Text.bold(container.command.originalCommand.replace('\n', ' '))
    }

    fun onEventPosted(event: TaskEvent) {
        when (event) {
            is ImageBuildProgressEvent -> onImageBuildProgressEventPosted(event)
            is ImageBuiltEvent -> onImageBuiltEventPosted(event)
            is ImagePullProgressEvent -> onImagePullProgressEventPosted(event)
            is ImagePulledEvent -> onImagePulledEventPosted(event)
            is TaskNetworkReadyEvent -> networkIsReady = true
            is ContainerCreatedEvent -> onContainerCreatedEventPosted(event)
            is ContainerStartedEvent -> onContainerStartedEventPosted(event)
            is ContainerBecameHealthyEvent -> onContainerBecameHealthyEventPosted(event)
            is ContainerBecameReadyEvent -> onContainerBecameReadyEventPosted(event)
            is RunningSetupCommandEvent -> onRunningSetupCommandEventPosted(event)
            is CachesInitialisedEvent -> cacheInitialised = true
            is StepStartingEvent -> onStepStarting(event.step)
        }
    }

    private fun onStepStarting(step: TaskStep) {
        when (step) {
            is BuildImageStep -> onBuildImageStepStarting(step)
            is PullImageStep -> onPullImageStepStarting(step)
            is CreateContainerStep -> onCreateContainerStepStarting(step)
            is RunContainerStep -> onRunContainerStepStarting(step)
        }
    }

    private fun onBuildImageStepStarting(step: BuildImageStep) {
        if (step.container == container) {
            isBuilding = true
        }
    }

    private fun onPullImageStepStarting(step: PullImageStep) {
        if (step.source == container.imageSource) {
            isPulling = true
        }
    }

    private fun onCreateContainerStepStarting(step: CreateContainerStep) {
        if (step.container == container) {
            isCreating = true
        }
    }

    private fun onRunContainerStepStarting(step: RunContainerStep) {
        if (step.container == container) {
            if (isTaskContainer) {
                isRunning = true
            } else {
                isStarting = true
            }
        }
    }

    private fun onImageBuildProgressEventPosted(event: ImageBuildProgressEvent) {
        if (event.container == container) {
            isBuilding = true
            lastBuildProgressUpdate = event.buildProgress
        }
    }

    private fun onImageBuiltEventPosted(event: ImageBuiltEvent) {
        if (event.container == container) {
            hasBeenBuilt = true
        }
    }

    private fun onImagePullProgressEventPosted(event: ImagePullProgressEvent) {
        if (container.imageSource == event.source) {
            isPulling = true
            lastProgressUpdate = event.progress
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
        if (event.container == container && !isTaskContainer) {
            hasStarted = true
        }
    }

    private fun onContainerBecameHealthyEventPosted(event: ContainerBecameHealthyEvent) {
        if (event.container == container) {
            isHealthy = true
        }
    }

    private fun onContainerBecameReadyEventPosted(event: ContainerBecameReadyEvent) {
        readyContainers.add(event.container)

        if (event.container == container) {
            isReady = true
        }
    }

    private fun onRunningSetupCommandEventPosted(event: RunningSetupCommandEvent) {
        if (event.container == container) {
            setupCommandState = SetupCommandState.Running(event.command, event.commandIndex)
        }
    }

    private sealed class SetupCommandState {
        data class Running(val command: SetupCommand, val index: Int) : SetupCommandState()
        object None : SetupCommandState()
        object NotStarted : SetupCommandState()
    }
}
