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

package batect.cli.commands

import batect.cli.CommandLineOptions
import batect.config.Configuration
import batect.config.PullImage
import batect.config.io.ConfigurationLoader
import batect.execution.SessionRunner
import batect.ioc.SessionKodeinFactory
import batect.ui.OutputStyle
import batect.updates.UpdateNotifier
import java.nio.file.Path
import org.kodein.di.DirectDI
import org.kodein.di.instance

class RunTaskCommand(
    private val configFile: Path,
    private val commandLineOptions: CommandLineOptions,
    private val configLoader: ConfigurationLoader,
    private val updateNotifier: UpdateNotifier,
    private val backgroundTaskManager: BackgroundTaskManager,
    private val dockerConnectivity: DockerConnectivity
) : Command {

    override fun run(): Int {
        val config = loadConfig()

        return dockerConnectivity.checkAndRun { kodein ->
            runPreExecutionOperations()
            runFromConfig(kodein, config)
        }
    }

    private fun loadConfig(): Configuration {
        val configFromFile = configLoader.loadConfig(configFile)
        val overrides = commandLineOptions.imageOverrides.mapValues { PullImage(it.value) }

        return configFromFile.applyImageOverrides(overrides)
    }

    private fun runPreExecutionOperations() {
        if (commandLineOptions.requestedOutputStyle != OutputStyle.Quiet) {
            updateNotifier.run()
        }

        backgroundTaskManager.startBackgroundTasks()
    }

    private fun runFromConfig(kodein: DirectDI, config: Configuration): Int {
        val sessionKodeinFactory = kodein.instance<SessionKodeinFactory>()
        val sessionKodein = sessionKodeinFactory.create(config)
        val sessionRunner = sessionKodein.instance<SessionRunner>()

        return sessionRunner.runTaskAndPrerequisites(commandLineOptions.taskName!!)
    }
}
