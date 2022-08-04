/*
    Copyright 2017-2022 Charles Korn.

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

import batect.config.Container
import batect.config.Task
import batect.ioc.TaskKodeinFactory
import batect.logging.Logger
import batect.telemetry.TelemetryCaptor
import batect.telemetry.TelemetrySpanBuilder
import batect.telemetry.addSpan
import batect.ui.Console
import batect.ui.EventLogger
import batect.ui.text.Text
import org.kodein.di.instance
import java.time.Duration
import java.time.Instant

data class TaskRunner(
    private val taskKodeinFactory: TaskKodeinFactory,
    private val interruptionTrap: InterruptionTrap,
    private val console: Console,
    private val telemetryCaptor: TelemetryCaptor,
    private val logger: Logger
) {
    fun run(task: Task, runOptions: RunOptions): TaskRunResult {
        return telemetryCaptor.addSpan("RunTask") { span ->
            runWithTelemetry(task, runOptions, span)
        }
    }

    private fun runWithTelemetry(task: Task, runOptions: RunOptions, telemetrySpanBuilder: TelemetrySpanBuilder): TaskRunResult {
        telemetrySpanBuilder.addAttribute("taskOnlyHasPrerequisites", task.runConfiguration == null)

        if (task.runConfiguration == null) {
            logger.info {
                message("Task has no run configuration, skipping.")
                data("taskName", task.name)
            }

            console.println(Text.white(Text("The task ") + Text.bold(task.name) + Text(" only defines prerequisite tasks, nothing more to do.")))

            return TaskRunResult(0, emptySet())
        }

        logger.info {
            message("Preparing task.")
            data("taskName", task.name)
        }

        val startTime = Instant.now()

        taskKodeinFactory.create(task, runOptions).use { kodein ->
            val eventLogger = kodein.instance<EventLogger>()
            eventLogger.onTaskStarting(task.name)
            telemetrySpanBuilder.addAttribute("containersInTask", kodein.instance<ContainerDependencyGraph>().allContainers.size)

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
            val containers = kodein.instance<ContainerDependencyGraph>().allContainers

            if (stateMachine.taskHasFailed) {
                return TaskRunResult(onTaskFailed(eventLogger, task, stateMachine), containers)
            }

            val duration = Duration.between(startTime, finishTime)

            return TaskRunResult(onTaskSucceeded(eventLogger, task, stateMachine, duration, runOptions), containers)
        }
    }

    private fun onTaskFailed(eventLogger: EventLogger, task: Task, stateMachine: TaskStateMachine): Int {
        eventLogger.onTaskFailed(task.name, stateMachine.postTaskManualCleanup, stateMachine.allEvents)

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
            eventLogger.onTaskFinishedWithCleanupDisabled(stateMachine.postTaskManualCleanup as PostTaskManualCleanup.Required, stateMachine.allEvents)
            return -1
        }

        return if (exitCode in Int.MIN_VALUE..Int.MAX_VALUE) {
            exitCode.toInt()
        } else {
            -1
        }
    }
}

data class TaskRunResult(val exitCode: Int, val containers: Set<Container>)
