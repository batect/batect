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

import batect.config.Container
import batect.config.Task
import batect.os.Command

class ContainerCommandResolver(private val runOptions: RunOptions) {
    fun resolveCommand(container: Container, task: Task): Command? {
        val baseCommand = resolveBaseCommand(container, task)

        if (extraArgsApply(container, task)) {
            if (baseCommand == null) {
                throw ContainerCommandResolutionException("Additional command line arguments for the task have been provided, but neither the task (${task.name}) nor the main task container (${container.name}) have an explicit command in the configuration file.")
            }

            return baseCommand + runOptions.additionalTaskCommandArguments
        }

        return baseCommand
    }

    private fun isTaskContainer(container: Container, task: Task): Boolean = container.name == task.runConfiguration?.container
    private fun isMainTask(task: Task): Boolean = task.name == runOptions.taskName
    private fun extraArgsApply(container: Container, task: Task): Boolean = isTaskContainer(container, task) && isMainTask(task) && runOptions.additionalTaskCommandArguments.any()

    private fun resolveBaseCommand(container: Container, task: Task): Command? {
        if (task.runConfiguration != null && isTaskContainer(container, task) && task.runConfiguration.command != null) {
            return task.runConfiguration.command
        }

        return container.command
    }
}

class ContainerCommandResolutionException(message: String) : Exception(message)
