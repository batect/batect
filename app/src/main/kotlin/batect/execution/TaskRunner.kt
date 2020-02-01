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

import batect.config.Configuration
import batect.config.Task
import batect.docker.client.DockerContainerType
import batect.ioc.TaskKodeinFactory
import batect.logging.Logger
import batect.ui.EventLogger
import org.kodein.di.generic.instance
import java.time.Duration
import java.time.Instant

data class TaskRunner(
    private val taskKodeinFactory: TaskKodeinFactory,
    private val interruptionTrap: InterruptionTrap,
    private val logger: Logger
) {
    fun run(config: Configuration, task: Task, runOptions: RunOptions, containerType: DockerContainerType): Int {
        logger.info {
            message("Preparing task.")
            data("taskName", task.name)
        }

        val startTime = Instant.now()
        val kodein = taskKodeinFactory.create(config, task, runOptions, containerType)
        val eventLogger = kodein.instance<EventLogger>()
        eventLogger.onTaskStarting(task.name)

        val executionManager = kodein.instance<ParallelExecutionManager>()

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

        val stateMachine = kodein.instance<TaskStateMachine>()

        if (stateMachine.taskHasFailed) {
            return onTaskFailed(eventLogger, task, stateMachine)
        }

        val duration = Duration.between(startTime, finishTime)

        return onTaskSucceeded(eventLogger, task, stateMachine, duration, runOptions)
    }

    private fun onTaskFailed(eventLogger: EventLogger, task: Task, stateMachine: TaskStateMachine): Int {
        eventLogger.onTaskFailed(task.name, stateMachine.manualCleanupInstructions)

        logger.warn {
            message("Task execution failed.")
            data("taskName", task.name)
        }

        return -1
    }

    private fun onTaskSucceeded(eventLogger: EventLogger, task: Task, stateMachine: TaskStateMachine, duration: Duration, runOptions: RunOptions): Int {
        eventLogger.onTaskFinished(task.name, stateMachine.taskExitCode, duration)

        logger.info {
            message("Task execution completed normally.")
            data("taskName", task.name)
            data("exitCode", stateMachine.taskExitCode)
        }

        if (runOptions.behaviourAfterSuccess == CleanupOption.DontCleanup) {
            eventLogger.onTaskFinishedWithCleanupDisabled(stateMachine.manualCleanupInstructions)
            return -1
        }

        return stateMachine.taskExitCode
    }
}
