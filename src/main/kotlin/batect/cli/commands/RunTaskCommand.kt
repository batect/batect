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

package batect.cli.commands

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.instance
import batect.TaskRunner
import batect.cli.CommonOptions
import batect.config.io.ConfigurationLoader

class RunTaskCommandDefinition : CommandDefinition("run", "Run a task.") {
    val taskName: String by RequiredPositionalParameter("TASK", "The name of the task to run.")

    override fun createCommand(kodein: Kodein): Command = RunTaskCommand(
            kodein.instance(CommonOptions.ConfigurationFileName),
            taskName,
            kodein.instance(),
            kodein.instance())
}

data class RunTaskCommand(
        val configFile: String,
        val taskName: String,
        val configLoader: ConfigurationLoader,
        val taskRunner: TaskRunner) : Command {

    override fun run(): Int {
        val config = configLoader.loadConfig(configFile)

        return taskRunner.run(config, taskName)
    }
}
