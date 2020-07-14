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

import batect.config.Task
import batect.ioc.TaskKodeinFactory
import batect.logging.Logger
import batect.ui.Console
import batect.ui.EventLogger
import batect.ui.text.Text
import java.time.Duration
import java.time.Instant
import org.kodein.di.generic.instance

data class TaskRunner(
    private val taskKodeinFactory: TaskKodeinFactory,
    private val interruptionTrap: InterruptionTrap,
    private val console: Console,
    private val logger: Logger
) {
    fun run(task: Task, runOptions: RunOptions): Int {
        if (task.runConfiguration == null) {
            logger.info {
                message("Task has no run configuration, skipping.")
                data("taskName", task.name)
            }

            console.println(Text.white(Text("The task ") + Text.bold(task.name) + Text(" only defines prerequisite tasks, nothing to do.")))

            return 0
        }

        logger.info {
            message("Preparing task.")
            data("taskName", task.name)
        }

        val startTime = Instant.now()

        taskKodeinFactory.create(task, runOptions).use { kodein ->
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
        val exitCode = stateMachine.taskExitCode
        eventLogger.onTaskFinished(task.name, exitCode, duration)

        logger.info {
            message("Task execution completed normally.")
            data("taskName", task.name)
            data("exitCode", exitCode)
        }

        if (runOptions.behaviourAfterSuccess == CleanupOption.DontCleanup) {
            eventLogger.onTaskFinishedWithCleanupDisabled(stateMachine.manualCleanupInstructions)
            return -1
        }

        return if (exitCode in Int.MIN_VALUE..Int.MAX_VALUE) {
            exitCode.toInt()
        } else {
            -1
        }
    }
}
