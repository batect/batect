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

package batect.cli.commands

import batect.config.io.ConfigurationLoader
import java.io.PrintStream

class ListTasksCommand(val configFile: String, val configLoader: ConfigurationLoader, val outputStream: PrintStream) : Command {
    override fun run(): Int {
        val config = configLoader.loadConfig(configFile)

        outputStream.println("Available tasks:")

        config.tasks
            .sortedBy { it.name }
            .forEach {
                outputStream.print("- ")
                outputStream.print(it.name)

                if (it.description.isNotBlank()) {
                    outputStream.print(": ")
                    outputStream.print(it.description)
                }

                outputStream.println()
            }

        return 0
    }
}
