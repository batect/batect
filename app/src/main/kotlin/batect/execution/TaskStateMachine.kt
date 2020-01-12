/*
   Copyright 2017-2020 Charles Korn.

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

package batect.execution

import batect.docker.client.DockerContainerType
import batect.execution.model.events.ContainerCreatedEvent
import batect.execution.model.events.TaskEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.events.TaskFailedEvent
import batect.execution.model.stages.CleanupStage
import batect.execution.model.stages.CleanupStagePlanner
import batect.execution.model.stages.NoStepsReady
import batect.execution.model.stages.RunStage
import batect.execution.model.stages.RunStagePlanner
import batect.execution.model.stages.Stage
import batect.execution.model.stages.StageComplete
import batect.execution.model.stages.StepReady
import batect.execution.model.steps.TaskStep
import batect.logging.Logger
import batect.ui.FailureErrorMessageFormatter
import batect.ui.text.TextRun
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class TaskStateMachine(
    val graph: ContainerDependencyGraph,
    val runOptions: RunOptions,
    val runStagePlanner: RunStagePlanner,
    val cleanupStagePlanner: CleanupStagePlanner,
    val failureErrorMessageFormatter: FailureErrorMessageFormatter,
    val cancellationContext: CancellationContext,
    val containerType: DockerContainerType,
    val logger: Logger
) : TaskEventSink {
    var taskHasFailed: Boolean = false
        private set

    var manualCleanupInstructions: TextRun = TextRun()
        private set

    private val events: MutableSet<TaskEvent> = mutableSetOf()
    private val lock = ReentrantLock()
    private var currentStage: Stage = runStagePlanner.createStage(graph, containerType)
    private var taskFailedDuringCleanup: Boolean = false

    fun popNextStep(stepsStillRunning: Boolean): TaskStep? {
        lock.withLock {
            logger.info {
                message("Trying to get next step to execute.")
                data("stepsStillRunning", stepsStillRunning)
                data("currentStage", currentStage.toString())
            }

            if (taskHasFailed && inRunStage()) {
                return handleDrainingWorkAfterRunFailure(stepsStillRunning)
            }

            return when (val result = currentStage.popNextStep(events, stepsStillRunning)) {
                is StepReady -> handleNextStepReady(result)
                is NoStepsReady -> handleNoStepsReady(stepsStillRunning)
                is StageComplete -> handleStageComplete(stepsStillRunning)
            }
        }
    }

    private fun handleDrainingWorkAfterRunFailure(stepsStillRunning: Boolean): TaskStep? {
        if (stepsStillRunning) {
            logger.info {
                message("Task has failed, not returning any further work while existing work completes.")
            }

            return null
        }

        logger.info {
            message("Task has failed and existing work has finished. Beginning cleanup.")
        }

        return startCleanupStage(stepsStillRunning)
    }

    private fun handleNextStepReady(result: StepReady): TaskStep {
        logger.info {
            message("Step is ready to execute.")
            data("step", result.step.toString())
        }

        return result.step
    }

    private fun handleNoStepsReady(stepsStillRunning: Boolean): Nothing? {
        if (!stepsStillRunning) {
            if (!taskFailedDuringCleanup) {
                taskHasFailed = true
                throw IllegalStateException("None of the remaining steps are ready to execute, but there are no steps currently running.")
            }
        }

        logger.info {
            message("No steps ready to execute.")
        }

        return null
    }

    private fun handleStageComplete(stepsStillRunning: Boolean): TaskStep? = when (currentStage) {
        is RunStage -> {
            logger.info {
                message("Run stage complete, switching to cleanup stage.")
            }

            startCleanupStage(stepsStillRunning)
        }
        is CleanupStage -> {
            logger.info {
                message("Cleanup stage complete. No work left to do.")
            }

            null
        }
        else -> throw IllegalArgumentException("Unknown stage type: ${currentStage::class.qualifiedName}")
    }

    private fun startCleanupStage(stepsStillRunning: Boolean): TaskStep? {
        val cleanupStage = cleanupStagePlanner.createStage(graph, events)

        if (taskHasFailed && shouldLeaveCreatedContainersRunningAfterFailure()) {
            manualCleanupInstructions = failureErrorMessageFormatter.formatManualCleanupMessageAfterTaskFailureWithCleanupDisabled(events, cleanupStage.manualCleanupInstructions)
            return null
        }

        if (runOptions.behaviourAfterSuccess == CleanupOption.DontCleanup) {
            logger.info {
                message("Cleanup after success has been disabled. Not cleaning up.")
            }

            manualCleanupInstructions = failureErrorMessageFormatter.formatManualCleanupMessageAfterTaskSuccessWithCleanupDisabled(events, cleanupStage.manualCleanupInstructions)
            return null
        }

        currentStage = cleanupStage

        logger.info {
            message("Returning first step from cleanup stage.")
        }

        return popNextStep(stepsStillRunning)
    }

    override fun postEvent(event: TaskEvent) {
        lock.withLock {
            logger.info {
                message("Event received.")
                data("event", event.toString())
            }

            events.add(event)

            if (event is TaskFailedEvent) {
                handleTaskFailedEvent()
            }
        }
    }

    private fun handleTaskFailedEvent() {
        taskHasFailed = true

        if (inCleanupStage()) {
            taskFailedDuringCleanup = true

            val manualCleanupCommands = (currentStage as CleanupStage).manualCleanupInstructions
            manualCleanupInstructions = failureErrorMessageFormatter.formatManualCleanupMessageAfterCleanupFailure(manualCleanupCommands)
        } else {
            cancellationContext.cancel()
        }
    }

    private fun shouldLeaveCreatedContainersRunningAfterFailure(): Boolean = runOptions.behaviourAfterFailure == CleanupOption.DontCleanup && events.any { it is ContainerCreatedEvent }
    private fun inRunStage(): Boolean = currentStage is RunStage
    private fun inCleanupStage(): Boolean = currentStage is CleanupStage

    // This method is not thread safe, and is designed to be used only after task execution has completed.
    fun getAllEvents(): Set<TaskEvent> = events
}
