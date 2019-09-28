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

package batect.execution

import batect.config.Configuration
import batect.config.Task
import batect.execution.model.events.RunningContainerExitedEvent
import batect.logging.Logger
import batect.ui.EventLogger
import batect.ui.EventLoggerProvider
import java.time.Duration
import java.time.Instant

data class TaskRunner(
    private val eventLoggerProvider: EventLoggerProvider,
    private val graphProvider: ContainerDependencyGraphProvider,
    private val stateMachineProvider: TaskStateMachineProvider,
    private val executionManagerProvider: ParallelExecutionManagerProvider,
    private val interruptionTrap: InterruptionTrap,
    private val logger: Logger
) {
    fun run(config: Configuration, task: Task, runOptions: RunOptions): Int {
        logger.info {
            message("Preparing task.")
            data("taskName", task.name)
        }

        val startTime = Instant.now()
        val graph = graphProvider.createGraph(config, task)
        val eventLogger = eventLoggerProvider.getEventLogger(graph, runOptions)
        eventLogger.onTaskStarting(task.name)

        val stateMachine = stateMachineProvider.createStateMachine(graph, runOptions)
        val executionManager = executionManagerProvider.createParallelExecutionManager(eventLogger, stateMachine, runOptions)

        logger.info {
            message("Preparation complete, starting task.")
            data("taskName", task.name)
        }

        interruptionTrap.trapInterruptions(executionManager).use {
            executionManager.run()
        }

        val finishTime = Instant.now()

        logger.info {
            message("Task execution completed.")
            data("taskName", task.name)
        }

        if (stateMachine.taskHasFailed) {
            return onTaskFailed(eventLogger, task, stateMachine)
        }

        val duration = Duration.between(startTime, finishTime)

        return onTaskSucceeded(eventLogger, task, stateMachine, graph, duration, runOptions)
    }

    private fun onTaskFailed(eventLogger: EventLogger, task: Task, stateMachine: TaskStateMachine): Int {
        eventLogger.onTaskFailed(task.name, stateMachine.manualCleanupInstructions)

        logger.warn {
            message("Task execution failed.")
            data("taskName", task.name)
        }

        return -1
    }

    private fun onTaskSucceeded(eventLogger: EventLogger, task: Task, stateMachine: TaskStateMachine, graph: ContainerDependencyGraph, duration: Duration, runOptions: RunOptions): Int {
        val containerExitedEvent = stateMachine.getAllEvents()
            .filterIsInstance<RunningContainerExitedEvent>()
            .singleOrNull { it.container == graph.taskContainerNode.container }

        if (containerExitedEvent == null) {
            throw IllegalStateException("The task neither failed nor succeeded.")
        }

        eventLogger.onTaskFinished(task.name, containerExitedEvent.exitCode, duration)

        logger.info {
            message("Task execution completed normally.")
            data("taskName", task.name)
            data("exitCode", containerExitedEvent.exitCode)
        }

        if (runOptions.behaviourAfterSuccess == CleanupOption.DontCleanup) {
            eventLogger.onTaskFinishedWithCleanupDisabled(stateMachine.manualCleanupInstructions)
            return -1
        }

        return containerExitedEvent.exitCode
    }
}
