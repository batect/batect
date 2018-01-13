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

package batect.model

import batect.config.Configuration
import batect.config.Task
import batect.logging.Logger

class TaskExecutionOrderResolver(private val logger: Logger) {
    fun resolveExecutionOrder(config: Configuration, taskName: String): List<Task> {
        val task = config.tasks[taskName]

        if (task == null) {
            throw TaskExecutionOrderResolutionException("The task '$taskName' does not exist.")
        }

        val allTasksToSchedule = resolvePrerequisitesForTask(config, task, listOf(task)) + task
        var remainingTasksToSchedule = allTasksToSchedule
        var scheduledTasks = emptyList<Task>()

        while (remainingTasksToSchedule.isNotEmpty()) {
            val tasksThatCanBeScheduled = remainingTasksToSchedule
                .filter { canBeScheduled(it, scheduledTasks) }

            if (tasksThatCanBeScheduled.isEmpty()) {
                throw RuntimeException("No tasks can be scheduled. (This should never happen, this should be caught earlier.)")
            }

            scheduledTasks += tasksThatCanBeScheduled
            remainingTasksToSchedule -= tasksThatCanBeScheduled
        }

        logger.info {
            message("Resolved task execution order.")
            data("executionOrder", scheduledTasks.map { it.name })
        }

        return scheduledTasks
    }

    private fun resolvePrerequisitesForTask(config: Configuration, parentTask: Task, path: List<Task>): Set<Task> {
        val prerequisites = parentTask.prerequisiteTasks
            .mapTo(mutableSetOf<Task>()) { prerequisiteTaskName -> resolvePrerequisiteForTask(config, parentTask, prerequisiteTaskName, path) }

        val childPrerequisites = prerequisites
            .flatMapTo(mutableSetOf<Task>()) { prerequisite -> resolvePrerequisitesForTask(config, prerequisite, path + prerequisite) }

        return prerequisites + childPrerequisites
    }

    private fun resolvePrerequisiteForTask(config: Configuration, parentTask: Task, prerequisiteTaskName: String, path: List<Task>): Task {
        val prerequisite = config.tasks[prerequisiteTaskName]

        if (prerequisite == null) {
            throw TaskExecutionOrderResolutionException("The task '$prerequisiteTaskName' given as a prerequisite of '${parentTask.name}' does not exist.")
        }

        if (path.contains(prerequisite)) {
            val description = cycleDescription(path + prerequisite)
            throw TaskExecutionOrderResolutionException("There is a dependency cycle between tasks: $description.")
        }

        return prerequisite
    }

    private fun cycleDescription(path: List<Task>): String {
        val taskNames = path.map { "'${it.name}'" }
        val firstPart = "task ${taskNames[0]} has ${taskNames[1]} as a prerequisite"

        val remainingNames = taskNames.drop(2)
        val remainingPart = remainingNames.map { ", which has $it as a prerequisite" }.joinToString("")

        return firstPart + remainingPart
    }

    private fun canBeScheduled(task: Task, tasksAlreadyScheduled: List<Task>): Boolean =
        tasksAlreadyScheduled.map { it.name }.containsAll(task.prerequisiteTasks)
}

class TaskExecutionOrderResolutionException(message: String) : RuntimeException(message)
