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

import batect.config.Configuration
import batect.config.PullImage
import batect.config.Task
import batect.config.io.ConfigurationLoader
import batect.execution.CleanupOption
import batect.execution.RunOptions
import batect.execution.TaskExecutionOrderResolutionException
import batect.execution.TaskExecutionOrderResolver
import batect.execution.TaskRunner
import batect.ioc.SessionKodeinFactory
import batect.logging.Logger
import batect.ui.Console
import batect.ui.OutputStyle
import batect.ui.text.Text
import batect.updates.UpdateNotifier
import java.nio.file.Path
import org.kodein.di.DKodein
import org.kodein.di.generic.instance

class RunTaskCommand(
    private val configFile: Path,
    private val runOptions: RunOptions,
    private val configLoader: ConfigurationLoader,
    private val taskExecutionOrderResolver: TaskExecutionOrderResolver,
    private val updateNotifier: UpdateNotifier,
    private val backgroundTaskManager: BackgroundTaskManager,
    private val dockerConnectivity: DockerConnectivity,
    private val requestedOutputStyle: OutputStyle?,
    private val console: Console,
    private val errorConsole: Console,
    private val logger: Logger
) : Command {

    override fun run(): Int {
        val config = loadConfig()

        return dockerConnectivity.checkAndRun { kodein ->
            runFromConfig(kodein, config)
        }
    }

    private fun loadConfig(): Configuration {
        val configFromFile = configLoader.loadConfig(configFile)
        val overrides = runOptions.imageOverrides.mapValues { PullImage(it.value) }

        return configFromFile.applyImageOverrides(overrides)
    }

    private fun runFromConfig(kodein: DKodein, config: Configuration): Int {
        try {
            val tasks = taskExecutionOrderResolver.resolveExecutionOrder(config, runOptions.taskName)

            if (requestedOutputStyle != OutputStyle.Quiet) {
                updateNotifier.run()
            }

            backgroundTaskManager.startBackgroundTasks()

            return runTasks(kodein, config, tasks)
        } catch (e: TaskExecutionOrderResolutionException) {
            logger.error {
                message("Could not resolve task execution order.")
                exception(e)
            }

            errorConsole.println(Text.red(e.message ?: ""))
            return -1
        }
    }

    private fun runTasks(kodein: DKodein, config: Configuration, tasks: List<Task>): Int {
        val sessionKodeinFactory = kodein.instance<SessionKodeinFactory>()
        val sessionKodein = sessionKodeinFactory.create(config)
        val taskRunner = sessionKodein.instance<TaskRunner>()

        for (task in tasks) {
            val isMainTask = task == tasks.last()
            val behaviourAfterSuccess = if (isMainTask) runOptions.behaviourAfterSuccess else CleanupOption.Cleanup
            val runOptionsForThisTask = runOptions.copy(behaviourAfterSuccess = behaviourAfterSuccess)

            val exitCode = taskRunner.run(task, runOptionsForThisTask)

            if (exitCode != 0) {
                return exitCode
            }

            if (!isMainTask && requestedOutputStyle != OutputStyle.Quiet) {
                console.println()
            }
        }

        return 0
    }
}
