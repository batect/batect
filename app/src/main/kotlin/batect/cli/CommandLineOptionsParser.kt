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

package batect.cli

import batect.cli.options.OptionParser
import batect.cli.options.OptionParserContainer
import batect.cli.options.OptionsParsingResult
import batect.cli.options.ValueConverters
import batect.cli.options.defaultvalues.EnvironmentVariableDefaultValueProviderFactory
import batect.cli.options.defaultvalues.LevelOfParallelismDefaultValueProvider
import batect.docker.DockerHttpConfigDefaults
import batect.os.PathResolverFactory
import batect.ui.OutputStyle
import java.nio.file.Path
import java.nio.file.Paths

class CommandLineOptionsParser(
    pathResolverFactory: PathResolverFactory,
    environmentVariableDefaultValueProviderFactory: EnvironmentVariableDefaultValueProviderFactory,
    dockerHttpConfigDefaults: DockerHttpConfigDefaults
) : OptionParserContainer {
    override val optionParser: OptionParser = OptionParser()

    companion object {
        const val disableCleanupAfterFailureFlagName = "no-cleanup-after-failure"
        const val disableCleanupAfterSuccessFlagName = "no-cleanup-after-success"
        const val disableCleanupFlagName = "no-cleanup"
        const val upgradeFlagName = "upgrade"
        const val helpBlurb = "For documentation and further information on batect, visit https://github.com/charleskorn/batect."
    }

    private val showHelp: Boolean by flagOption("help", "Show this help information and exit.")
    private val showVersionInfo: Boolean by flagOption("version", "Show batect version information and exit.")
    private val runUpgrade: Boolean by flagOption(upgradeFlagName, "Upgrade batect to the latest available version.")
    private val listTasks: Boolean by flagOption("list-tasks", "List available tasks and exit.")

    private val disableColorOutput: Boolean by flagOption("no-color", "Disable colored output from batect. Does not affect task command output. (implies --output=simple)")
    private val disableUpdateNotification: Boolean by flagOption("no-update-notification", "Disable checking for updates to batect and notifying you when a new version is available.")

    private val configurationFileName: Path by valueOption(
        "config-file",
        "The configuration file to use.",
        Paths.get("batect.yml"),
        ValueConverters.pathToFile(pathResolverFactory),
        'f'
    )

    private val logFileName: Path? by valueOption(
        "log-file",
        "Write internal batect logs to file.",
        ValueConverters.pathToFile(pathResolverFactory)
    )

    private val requestedOutputStyle: OutputStyle? by valueOption(
        "output",
        "Force a particular style of output from batect (does not affect task command output). Valid values are: fancy (default value if your console supports this), simple (no updating text), or quiet (only error messages).",
        ValueConverters.optionalEnum(),
        'o'
    )

    private val levelOfParallelism: Int by valueOption(
        "level-of-parallelism",
        "Maximum number of operations to run in parallel.",
        LevelOfParallelismDefaultValueProvider,
        ValueConverters::positiveInteger,
        'p'
    )

    private val disableCleanupAfterFailure: Boolean by flagOption(disableCleanupAfterFailureFlagName, "If an error occurs before any task can start, leave all containers created for that task running so that the issue can be investigated.")
    private val disableCleanupAfterSuccess: Boolean by flagOption(disableCleanupAfterSuccessFlagName, "If the main task succeeds, leave all containers created for that task running.")
    private val disableCleanup: Boolean by flagOption(disableCleanupFlagName, "Equivalent to providing both --$disableCleanupAfterFailureFlagName and --$disableCleanupAfterSuccessFlagName.")
    private val dontPropagateProxyEnvironmentVariables: Boolean by flagOption("no-proxy-vars", "Don't propagate proxy-related environment variables such as http_proxy and no_proxy to image builds or containers.")

    private val dockerHost: String by valueOption(
        "docker-host",
        "Docker host to use, in the format 'unix:///var/run/docker.sock', 'npipe:////./pipe/docker_engine', 'tcp://1.2.3.4:5678' or 'http://1.2.3.4:5678'.",
        environmentVariableDefaultValueProviderFactory.create("DOCKER_HOST", dockerHttpConfigDefaults.defaultDockerHost)
    )

    fun parse(args: Iterable<String>): CommandLineOptionsParsingResult {
        when (val result = optionParser.parseOptions(args)) {
            is OptionsParsingResult.InvalidOptions -> return CommandLineOptionsParsingResult.Failed(result.message)
            is OptionsParsingResult.ReadOptions -> return parseTaskName(args.drop(result.argumentsConsumed))
        }
    }

    private fun parseTaskName(remainingArgs: Iterable<String>): CommandLineOptionsParsingResult {
        if (requestedOutputStyle == OutputStyle.Fancy && disableColorOutput) {
            return CommandLineOptionsParsingResult.Failed("Fancy output mode cannot be used when colored output has been disabled.")
        }

        if (showHelp || showVersionInfo || listTasks || runUpgrade) {
            return CommandLineOptionsParsingResult.Succeeded(createOptionsObject(null, emptyList()))
        }

        when (remainingArgs.count()) {
            0 -> return CommandLineOptionsParsingResult.Failed("No task name provided.")
            1 -> {
                return CommandLineOptionsParsingResult.Succeeded(createOptionsObject(remainingArgs.first(), emptyList()))
            }
            else -> {
                val taskName = remainingArgs.first()
                val additionalArgs = remainingArgs.drop(1)

                if (additionalArgs.first() != "--") {
                    return CommandLineOptionsParsingResult.Failed(
                        "Too many arguments provided. The first extra argument is '${additionalArgs.first()}'.\n" +
                            "To pass additional arguments to the task command, prefix them with '--', for example, './batect my-task -- --extra-option-1 --extra-option-2 value'."
                    )
                }

                val additionalTaskCommandArguments = additionalArgs.drop(1)
                return CommandLineOptionsParsingResult.Succeeded(createOptionsObject(taskName, additionalTaskCommandArguments))
            }
        }
    }

    private fun createOptionsObject(taskName: String?, additionalTaskCommandArguments: Iterable<String>) = CommandLineOptions(
        showHelp = showHelp,
        showVersionInfo = showVersionInfo,
        runUpgrade = runUpgrade,
        listTasks = listTasks,
        configurationFileName = configurationFileName,
        logFileName = logFileName,
        requestedOutputStyle = requestedOutputStyle,
        disableColorOutput = disableColorOutput,
        disableUpdateNotification = disableUpdateNotification,
        levelOfParallelism = levelOfParallelism,
        disableCleanupAfterFailure = disableCleanupAfterFailure || disableCleanup,
        disableCleanupAfterSuccess = disableCleanupAfterSuccess || disableCleanup,
        dontPropagateProxyEnvironmentVariables = dontPropagateProxyEnvironmentVariables,
        taskName = taskName,
        additionalTaskCommandArguments = additionalTaskCommandArguments,
        dockerHost = dockerHost
    )
}

sealed class CommandLineOptionsParsingResult {
    data class Succeeded(val options: CommandLineOptions) : CommandLineOptionsParsingResult()
    data class Failed(val message: String) : CommandLineOptionsParsingResult()
}
