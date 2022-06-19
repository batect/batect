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

import batect.cli.CommandLineOptions
import batect.config.Task
import batect.telemetry.TelemetrySessionBuilder
import batect.ui.Console
import batect.ui.OutputStyle

class SessionRunner(
    private val taskExecutionOrderResolver: TaskExecutionOrderResolver,
    private val commandLineOptions: CommandLineOptions,
    private val taskRunner: TaskRunner,
    private val console: Console,
    private val imageTaggingValidator: ImageTaggingValidator,
    private val telemetrySessionBuilder: TelemetrySessionBuilder
) {
    fun runTaskAndPrerequisites(taskName: String): Int {
        val tasks = taskExecutionOrderResolver.resolveExecutionOrder(taskName)
        telemetrySessionBuilder.addAttribute("totalTasksToExecute", tasks.size)

        return runTasks(tasks)
    }

    private fun runTasks(tasks: List<Task>): Int {
        for (task in tasks) {
            val isMainTask = task == tasks.last()
            val exitCode = runTask(task, isMainTask)

            if (exitCode != 0) {
                return exitCode
            }

            if (!isMainTask && commandLineOptions.requestedOutputStyle != OutputStyle.Quiet) {
                console.println()
            }
        }

        val untaggedImages = imageTaggingValidator.checkForUntaggedContainers()

        if (untaggedImages.isNotEmpty()) {
            throw UntaggedImagesException(untaggedImages)
        }

        return 0
    }

    private fun runTask(task: Task, isMainTask: Boolean): Int {
        val runOptions = RunOptions(isMainTask, commandLineOptions)
        val result = taskRunner.run(task, runOptions)

        imageTaggingValidator.notifyContainersUsed(result.containers)

        return result.exitCode
    }
}
