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

package batect.cli.commands

import batect.cli.CommandLineOptions
import batect.config.RawConfiguration
import batect.config.Task
import batect.config.io.ConfigurationLoader
import batect.ui.OutputStyle
import java.io.PrintStream

class ListTasksCommand(
    val configLoader: ConfigurationLoader,
    val commandLineOptions: CommandLineOptions,
    val outputStream: PrintStream
) : Command {
    override fun run(): Int {
        val config = configLoader.loadConfig(commandLineOptions.configurationFileName).configuration

        when (commandLineOptions.requestedOutputStyle) {
            OutputStyle.Quiet -> printMachineReadableFormat(config)
            else -> printHumanReadableFormat(config)
        }

        return 0
    }

    private fun printMachineReadableFormat(config: RawConfiguration) {
        config.tasks
            .sortedBy { it.name }
            .forEach {
                outputStream.print(it.name)

                if (it.description.isNotBlank()) {
                    outputStream.print('\t')
                    outputStream.println(it.description)
                } else {
                    outputStream.println()
                }
            }
    }

    private fun printHumanReadableFormat(config: RawConfiguration) {
        val groups = config.tasks.groupBy { it.group }
        val allTasksHaveNoGroup = config.tasks.all { it: Task -> it.group == "" }

        groups.entries
            .sortedWith(groupComparator)
            .forEachIndexed { index, (groupName, tasks) ->
                when {
                    allTasksHaveNoGroup -> outputStream.println("Available tasks:")
                    groupName == "" -> outputStream.println("Ungrouped tasks:")
                    else -> outputStream.println("$groupName:")
                }

                printGroup(tasks)

                if (index < groups.count() - 1) {
                    outputStream.println()
                }
            }
    }

    private val groupComparator = Comparator<Map.Entry<String, List<Task>>> { (a, _), (b, _) ->
        when {
            a == b -> 0
            a == "" -> 1
            b == "" -> -1
            else -> a.compareTo(b)
        }
    }

    private fun printGroup(tasks: List<Task>) = tasks
        .sortedBy { it.name }
        .forEach { printTask(it) }

    private fun printTask(task: Task) {
        outputStream.print("- ")
        outputStream.print(task.name)

        if (task.description.isNotBlank()) {
            outputStream.print(": ")
            outputStream.print(task.description)
        }

        outputStream.println()
    }
}
