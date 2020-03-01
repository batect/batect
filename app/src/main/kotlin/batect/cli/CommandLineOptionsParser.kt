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

package batect.cli

import batect.cli.options.OptionGroup
import batect.cli.options.OptionParser
import batect.cli.options.OptionParserContainer
import batect.cli.options.OptionValueSource
import batect.cli.options.OptionsParsingResult
import batect.cli.options.ValueConverters
import batect.cli.options.defaultvalues.EnvironmentVariableDefaultValueProviderFactory
import batect.cli.options.defaultvalues.FileDefaultValueProvider
import batect.docker.DockerHttpConfigDefaults
import batect.execution.CacheType
import batect.os.PathResolverFactory
import batect.os.SystemInfo
import batect.ui.OutputStyle
import java.nio.file.Path
import java.nio.file.Paths

class CommandLineOptionsParser(
    pathResolverFactory: PathResolverFactory,
    environmentVariableDefaultValueProviderFactory: EnvironmentVariableDefaultValueProviderFactory,
    dockerHttpConfigDefaults: DockerHttpConfigDefaults,
    systemInfo: SystemInfo
) : OptionParserContainer {
    override val optionParser: OptionParser = OptionParser()

    companion object {
        const val disableCleanupAfterFailureFlagName = "no-cleanup-after-failure"
        const val disableCleanupAfterSuccessFlagName = "no-cleanup-after-success"
        const val disableCleanupFlagName = "no-cleanup"
        const val upgradeFlagName = "upgrade"
        const val configVariableOptionName = "config-var"
        const val helpBlurb = "For documentation and further information on batect, visit https://github.com/batect/batect."
    }

    private val cacheOptionsGroup = OptionGroup("Cache management options")
    private val dockerConnectionOptionsGroup = OptionGroup("Docker connection options")
    private val executionOptionsGroup = OptionGroup("Execution options")
    private val outputOptionsGroup = OptionGroup("Output options")
    private val helpOptionsGroup = OptionGroup("Help options")

    private val showHelp: Boolean by flagOption(helpOptionsGroup, "help", "Show this help information and exit.")
    private val showVersionInfo: Boolean by flagOption(helpOptionsGroup, "version", "Show batect version information and exit.")
    private val runUpgrade: Boolean by flagOption(helpOptionsGroup, upgradeFlagName, "Upgrade batect to the latest available version.")
    private val listTasks: Boolean by flagOption(helpOptionsGroup, "list-tasks", "List available tasks and exit.", 'T')
    private val runCleanup: Boolean by flagOption(cacheOptionsGroup, "clean", "Cleanup caches created on previous runs and exit.")

    private val disableColorOutput: Boolean by flagOption(outputOptionsGroup, "no-color", "Disable colored output from batect. Does not affect task command output. Implies --output=simple unless overridden.")
    private val disableUpdateNotification: Boolean by flagOption(executionOptionsGroup, "no-update-notification", "Disable checking for updates to batect and notifying you when a new version is available.")

    private val configurationFileName: Path by valueOption(
        executionOptionsGroup,
        "config-file",
        "The configuration file to use.",
        Paths.get("batect.yml"),
        ValueConverters.pathToFile(pathResolverFactory, mustExist = true),
        'f'
    )

    private val configVariablesSourceFileNameOption = valueOption(
        executionOptionsGroup,
        "config-vars-file",
        "YAML file containing values for config variables. Values in file take precedence over default values.",
        FileDefaultValueProvider("batect.local.yml", pathResolverFactory),
        ValueConverters.pathToFile(pathResolverFactory, mustExist = true)
    )

    private val configVariablesSourceFileName: Path? by configVariablesSourceFileNameOption

    private val configVariableOverrides: Map<String, String> by mapOption(
        executionOptionsGroup,
        configVariableOptionName,
        "Set a value for a config variable. Takes precedence over default values and values in file provided to ${configVariablesSourceFileNameOption.longOption}."
    )

    private val imageOverrides: Map<String, String> by mapOption(
        executionOptionsGroup,
        "override-image",
        "Override the image used by a container.",
        "<container>=<image>"
    )

    private val logFileName: Path? by valueOption(
        helpOptionsGroup,
        "log-file",
        "Write internal batect logs to file.",
        ValueConverters.pathToFile(pathResolverFactory)
    )

    private val requestedOutputStyle: OutputStyle? by valueOption<OutputStyle?, OutputStyle>(
        outputOptionsGroup,
        "output",
        "Force a particular style of output from batect (does not affect task command output). Valid values are: fancy (default value if your console supports this), simple (no updating text), all (interleaved output from all containers) or quiet (only error messages).",
        null,
        ValueConverters.enum(),
        'o'
    )

    private val disableCleanupAfterFailure: Boolean by flagOption(executionOptionsGroup, disableCleanupAfterFailureFlagName, "If an error occurs before any task can start, leave all containers created for that task running so that the issue can be investigated.")
    private val disableCleanupAfterSuccess: Boolean by flagOption(executionOptionsGroup, disableCleanupAfterSuccessFlagName, "If the main task succeeds, leave all containers created for that task running.")
    private val disableCleanup: Boolean by flagOption(executionOptionsGroup, disableCleanupFlagName, "Equivalent to providing both --$disableCleanupAfterFailureFlagName and --$disableCleanupAfterSuccessFlagName.")
    private val dontPropagateProxyEnvironmentVariables: Boolean by flagOption(executionOptionsGroup, "no-proxy-vars", "Don't propagate proxy-related environment variables such as http_proxy and no_proxy to image builds or containers.")

    private val cacheType: CacheType by valueOption(
        cacheOptionsGroup,
        "cache-type",
        "Storage mechanism to use for caches. Valid values are: 'volume' (use Docker volumes) or 'directory' (use directories mounted from the host).",
        environmentVariableDefaultValueProviderFactory.create("BATECT_CACHE_TYPE", CacheType.Volume, CacheType.Volume.name.toLowerCase(), ValueConverters.enum()),
        ValueConverters.enum()
    )

    private val dockerHost: String by valueOption(
        dockerConnectionOptionsGroup,
        "docker-host",
        "Docker host to use, in the format 'unix:///var/run/docker.sock', 'npipe:////./pipe/docker_engine' or 'tcp://1.2.3.4:5678'.",
        environmentVariableDefaultValueProviderFactory.create("DOCKER_HOST", dockerHttpConfigDefaults.defaultDockerHost, ValueConverters::string)
    )

    private val dockerUseTLSOption = flagOption(
        dockerConnectionOptionsGroup,
        "docker-tls",
        "Use TLS when communicating with the Docker host, but don't verify the certificate presented by the Docker host."
    )

    private val dockerUseTLS: Boolean by dockerUseTLSOption

    private val dockerVerifyTLSOption = flagOption(
        dockerConnectionOptionsGroup,
        "docker-tls-verify",
        "Use TLS when communicating with the Docker host and verify the certificate presented by the Docker host. Implies ${dockerUseTLSOption.longOption}.",
        environmentVariableDefaultValueProviderFactory.create("DOCKER_TLS_VERIFY", false, ValueConverters::boolean)
    )

    private val dockerVerifyTLS: Boolean by dockerVerifyTLSOption

    private val dockerDirectory = systemInfo.homeDirectory.resolve(".docker")

    private val dockerCertificateDirectoryOption = valueOption(
        dockerConnectionOptionsGroup,
        "docker-cert-path",
        "Path to directory containing certificates to use to provide authentication to the Docker host and authenticate the Docker host. Has no effect if ${dockerUseTLSOption.longOption} is not set.",
        environmentVariableDefaultValueProviderFactory.create("DOCKER_CERT_PATH", null, ValueConverters.pathToDirectory(pathResolverFactory, mustExist = true)),
        ValueConverters.pathToDirectory(pathResolverFactory, mustExist = true)
    )

    private val dockerCertificateDirectory: Path? by dockerCertificateDirectoryOption

    private val dockerTLSCACertificatePathOption = valueOption(
        dockerConnectionOptionsGroup,
        "docker-tls-ca-cert",
        "Path to TLS CA certificate file that should be used to verify certificates presented by the Docker host. Has no effect if ${dockerVerifyTLSOption.longOption} is not set, and takes precedence over ${dockerCertificateDirectoryOption.longOption}.",
        dockerDirectory.resolve("ca.pem"),
        ValueConverters.pathToFile(pathResolverFactory, mustExist = true)
    )

    private val dockerTlsCACertificatePath: Path by dockerTLSCACertificatePathOption

    private val dockerTLSCertificatePathOption = valueOption(
        dockerConnectionOptionsGroup,
        "docker-tls-cert",
        "Path to TLS certificate file to use to authenticate to the Docker host. Has no effect if ${dockerUseTLSOption.longOption} is not set, and takes precedence over ${dockerCertificateDirectoryOption.longOption}.",
        dockerDirectory.resolve("cert.pem"),
        ValueConverters.pathToFile(pathResolverFactory, mustExist = true)
    )

    private val dockerTLSCertificatePath: Path by dockerTLSCertificatePathOption

    private val dockerTLSKeyPathOption = valueOption(
        dockerConnectionOptionsGroup,
        "docker-tls-key",
        "Path to TLS key file to use to authenticate to the Docker host. Has no effect if ${dockerUseTLSOption.longOption} is not set, and takes precedence over ${dockerCertificateDirectoryOption.longOption}.",
        dockerDirectory.resolve("key.pem"),
        ValueConverters.pathToFile(pathResolverFactory, mustExist = true)
    )

    private val dockerTLSKeyPath: Path by dockerTLSKeyPathOption

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

        if (showHelp || showVersionInfo || listTasks || runUpgrade || runCleanup) {
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

    private fun resolvePathToDockerCertificate(path: Path, valueSource: OptionValueSource, defaultFileName: String): Path {
        return if (dockerCertificateDirectory == null || valueSource == OptionValueSource.CommandLine) {
            path
        } else {
            dockerCertificateDirectory!!.resolve(defaultFileName)
        }
    }

    private fun createOptionsObject(taskName: String?, additionalTaskCommandArguments: Iterable<String>) = CommandLineOptions(
        showHelp = showHelp,
        showVersionInfo = showVersionInfo,
        runUpgrade = runUpgrade,
        runCleanup = runCleanup,
        listTasks = listTasks,
        configurationFileName = configurationFileName,
        configVariablesSourceFile = configVariablesSourceFileName,
        imageOverrides = imageOverrides,
        logFileName = logFileName,
        requestedOutputStyle = requestedOutputStyle,
        disableColorOutput = disableColorOutput,
        disableUpdateNotification = disableUpdateNotification,
        disableCleanupAfterFailure = disableCleanupAfterFailure || disableCleanup,
        disableCleanupAfterSuccess = disableCleanupAfterSuccess || disableCleanup,
        dontPropagateProxyEnvironmentVariables = dontPropagateProxyEnvironmentVariables,
        taskName = taskName,
        additionalTaskCommandArguments = additionalTaskCommandArguments,
        configVariableOverrides = configVariableOverrides,
        dockerHost = dockerHost,
        dockerUseTLS = dockerUseTLS || dockerVerifyTLS,
        dockerVerifyTLS = dockerVerifyTLS,
        dockerTLSKeyPath = resolvePathToDockerCertificate(dockerTLSKeyPath, dockerTLSKeyPathOption.valueSource, "key.pem"),
        dockerTLSCertificatePath = resolvePathToDockerCertificate(dockerTLSCertificatePath, dockerTLSCertificatePathOption.valueSource, "cert.pem"),
        dockerTlsCACertificatePath = resolvePathToDockerCertificate(dockerTlsCACertificatePath, dockerTLSCACertificatePathOption.valueSource, "ca.pem"),
        cacheType = cacheType
    )
}

sealed class CommandLineOptionsParsingResult {
    data class Succeeded(val options: CommandLineOptions) : CommandLineOptionsParsingResult()
    data class Failed(val message: String) : CommandLineOptionsParsingResult()
}
