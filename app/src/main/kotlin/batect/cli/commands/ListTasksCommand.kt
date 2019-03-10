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

package batect.cli.commands

import batect.config.Task
import batect.config.io.ConfigurationLoader
import java.io.PrintStream
import java.nio.file.Path

class ListTasksCommand(val configFile: Path, val configLoader: ConfigurationLoader, val outputStream: PrintStream) : Command {
    override fun run(): Int {
        val config = configLoader.loadConfig(configFile)
        val groups = config.tasks.groupBy { it.group }
        val allTasksHaveNoGroup = config.tasks.all { it: Task -> it.group == "" }

        groups.entries
            .sortedWith(groupComparator)
            .forEachIndexed { index, (groupName, tasks) ->
                if (allTasksHaveNoGroup) {
                    outputStream.println("Available tasks:")
                } else if (groupName == "") {
                    outputStream.println("Ungrouped tasks:")
                } else {
                    outputStream.println("$groupName:")
                }

                printGroup(tasks)

                if (index < groups.count() - 1) {
                    outputStream.println()
                }
            }

        return 0
    }

    private val groupComparator = Comparator<Map.Entry<String, List<Task>>> { (a, _), (b, _) ->
        if (a == b) {
            0
        } else if (a == "") {
            1
        } else if (b == "") {
            -1
        } else {
            a.compareTo(b)
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
