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
import batect.docker.client.DockerConnectivityCheckResult
import batect.docker.client.DockerContainerType
import batect.docker.client.DockerSystemInfoClient
import batect.execution.CleanupOption
import batect.execution.ConfigVariablesProvider
import batect.execution.RunOptions
import batect.execution.TaskExecutionOrderResolutionException
import batect.execution.TaskExecutionOrderResolver
import batect.execution.TaskRunner
import batect.ioc.SessionKodeinFactory
import batect.logging.Logger
import batect.ui.Console
import batect.ui.text.Text
import batect.updates.UpdateNotifier
import org.kodein.di.generic.instance
import java.nio.file.Path

class RunTaskCommand(
    val configFile: Path,
    val runOptions: RunOptions,
    val configLoader: ConfigurationLoader,
    val configVariablesProvider: ConfigVariablesProvider,
    val taskExecutionOrderResolver: TaskExecutionOrderResolver,
    val sessionKodeinFactory: SessionKodeinFactory,
    val updateNotifier: UpdateNotifier,
    val dockerSystemInfoClient: DockerSystemInfoClient,
    val console: Console,
    val errorConsole: Console,
    val logger: Logger
) : Command {

    override fun run(): Int {
        val config = loadConfig()
        configVariablesProvider.build(config)

        return when (val connectivityCheckResult = dockerSystemInfoClient.checkConnectivity()) {
            is DockerConnectivityCheckResult.Failed -> {
                errorConsole.println(Text.red("Docker is not installed, not running or not compatible with batect: ${connectivityCheckResult.message}"))
                -1
            }
            is DockerConnectivityCheckResult.Succeeded -> {
                runFromConfig(config, connectivityCheckResult.containerType)
            }
        }
    }

    private fun loadConfig(): Configuration {
        val configFromFile = configLoader.loadConfig(configFile)
        val overrides = runOptions.imageOverrides.mapValues { PullImage(it.value) }

        return configFromFile.applyImageOverrides(overrides)
    }

    private fun runFromConfig(config: Configuration, containerType: DockerContainerType): Int {
        try {
            val tasks = taskExecutionOrderResolver.resolveExecutionOrder(config, runOptions.taskName)

            updateNotifier.run()

            return runTasks(config, tasks, containerType)
        } catch (e: TaskExecutionOrderResolutionException) {
            logger.error {
                message("Could not resolve task execution order.")
                exception(e)
            }

            errorConsole.println(Text.red(e.message ?: ""))
            return -1
        }
    }

    private fun runTasks(config: Configuration, tasks: List<Task>, containerType: DockerContainerType): Int {
        val sessionKodein = sessionKodeinFactory.create(config, containerType)
        val taskRunner = sessionKodein.instance<TaskRunner>()

        for (task in tasks) {
            val isMainTask = task == tasks.last()
            val behaviourAfterSuccess = if (isMainTask) runOptions.behaviourAfterSuccess else CleanupOption.Cleanup
            val runOptionsForThisTask = runOptions.copy(behaviourAfterSuccess = behaviourAfterSuccess)

            val exitCode = taskRunner.run(task, runOptionsForThisTask)

            if (exitCode != 0) {
                return exitCode
            }

            if (!isMainTask) {
                console.println()
            }
        }

        return 0
    }
}
