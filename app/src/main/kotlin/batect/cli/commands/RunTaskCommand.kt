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

package batect.cli.commands

import batect.cli.CommandLineOptions
import batect.config.RawConfiguration
import batect.config.io.ConfigurationLoader
import batect.execution.SessionRunner
import batect.ioc.SessionKodeinFactory
import batect.telemetry.TelemetryConsentPrompt
import batect.ui.OutputStyle
import batect.updates.UpdateNotifier
import org.kodein.di.DirectDI
import org.kodein.di.instance

class RunTaskCommand(
    private val commandLineOptions: CommandLineOptions,
    private val configLoader: ConfigurationLoader,
    private val telemetryConsentPrompt: TelemetryConsentPrompt,
    private val updateNotifier: UpdateNotifier,
    private val backgroundTaskManager: BackgroundTaskManager,
    private val dockerConnectivity: DockerConnectivity,
) : Command {
    override fun run(): Int {
        val config = configLoader.loadConfig(commandLineOptions.configurationFileName).configuration

        runPreExecutionOperations()

        return dockerConnectivity.checkAndRun { kodein ->
            runFromConfig(kodein, config)
        }
    }

    private fun runPreExecutionOperations() {
        telemetryConsentPrompt.askForConsentIfRequired()

        if (commandLineOptions.requestedOutputStyle != OutputStyle.Quiet) {
            updateNotifier.run()
        }

        backgroundTaskManager.startBackgroundTasks()
    }

    private fun runFromConfig(kodein: DirectDI, config: RawConfiguration): Int {
        val sessionKodeinFactory = kodein.instance<SessionKodeinFactory>()
        val sessionKodein = sessionKodeinFactory.create(config)
        val sessionRunner = sessionKodein.instance<SessionRunner>()

        return sessionRunner.runTaskAndPrerequisites(commandLineOptions.taskName!!)
    }
}
