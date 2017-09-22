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

package batect.model

import batect.config.Configuration
import batect.config.Task

class TaskExecutionOrderResolver {
    fun resolveExecutionOrder(config: Configuration, taskName: String): List<Task> {
        val task = config.tasks[taskName]

        if (task == null) {
            throw TaskExecutionOrderResolutionException("The task '$taskName' does not exist.")
        }

        val allTasksToSchedule = resolveDependenciesForTask(config, task, listOf(task)) + task
        var remainingTasksToSchedule = allTasksToSchedule
        var scheduledTasks = emptyList<Task>()

        while (remainingTasksToSchedule.isNotEmpty()) {
            val tasksThatCanBeScheduled = remainingTasksToSchedule
                .filter { canBeScheduled(it, scheduledTasks) }

            scheduledTasks += tasksThatCanBeScheduled
            remainingTasksToSchedule -= tasksThatCanBeScheduled
        }

        return scheduledTasks
    }

    private fun resolveDependenciesForTask(config: Configuration, parentTask: Task, path: List<Task>): Set<Task> {
        val dependencies = parentTask.dependsOnTasks
            .mapTo(mutableSetOf<Task>()) { dependencyTaskName -> resolveDependencyForTask(config, parentTask, dependencyTaskName, path) }

        val childDependencies = dependencies
            .flatMapTo(mutableSetOf<Task>()) { dependency -> resolveDependenciesForTask(config, dependency, path + dependency) }

        return dependencies + childDependencies
    }

    private fun resolveDependencyForTask(config: Configuration, parentTask: Task, dependencyTaskName: String, path: List<Task>): Task {
        val dependency = config.tasks[dependencyTaskName]

        if (dependency == null) {
            throw TaskExecutionOrderResolutionException("The task '$dependencyTaskName' given as a dependency of '${parentTask.name}' does not exist.")
        }

        if (path.contains(dependency)) {
            val description = cycleDescription(path + dependency)
            throw TaskExecutionOrderResolutionException("There is a dependency cycle between tasks: $description.")
        }

        return dependency
    }

    private fun cycleDescription(path: List<Task>): String {
        val taskNames = path.map { "'${it.name}'" }
        val firstPart = "task ${taskNames[0]} depends on ${taskNames[1]}"

        val remainingNames = taskNames.drop(2)
        val remainingPart = remainingNames.map { ", which depends on $it" }.joinToString("")

        return firstPart + remainingPart
    }

    private fun canBeScheduled(task: Task, tasksAlreadyScheduled: List<Task>): Boolean
        = tasksAlreadyScheduled.map { it.name }.containsAll(task.dependsOnTasks)
}

class TaskExecutionOrderResolutionException(message: String) : RuntimeException(message)
