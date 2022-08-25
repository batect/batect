/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.cli

import batect.cli.commands.completion.Shell
import batect.execution.CacheType
import batect.ioc.rootModule
import batect.logging.FileLogSink
import batect.logging.LogSink
import batect.logging.NullLogSink
import batect.ui.OutputStyle
import org.kodein.di.DirectDI
import org.kodein.di.bind
import org.kodein.di.direct
import org.kodein.di.instance
import org.kodein.di.singleton
import org.kodein.di.subDI
import java.nio.file.Path
import java.nio.file.Paths

data class CommandLineOptions(
    val showHelp: Boolean = false,
    val showVersionInfo: Boolean = false,
    val runUpgrade: Boolean = false,
    val listTasks: Boolean = false,
    val runCleanup: Boolean = false,
    val permanentlyDisableTelemetry: Boolean = false,
    val permanentlyEnableTelemetry: Boolean = false,
    val configurationFileName: Path = Paths.get("batect.yml"),
    val configVariablesSourceFile: Path? = null,
    val logFileName: Path? = null,
    val requestedOutputStyle: OutputStyle? = null,
    val disableColorOutput: Boolean = false,
    val disableUpdateNotification: Boolean = false,
    val disableWrapperCacheCleanup: Boolean = false,
    val disableCleanupAfterFailure: Boolean = false,
    val disableCleanupAfterSuccess: Boolean = false,
    val disablePortMappings: Boolean = false,
    val dontPropagateProxyEnvironmentVariables: Boolean = false,
    val taskName: String? = null,
    val additionalTaskCommandArguments: Iterable<String> = emptyList(),
    val configVariableOverrides: Map<String, String> = emptyMap(),
    val imageOverrides: Map<String, String> = emptyMap(),
    val imageTags: Map<String, Set<String>> = emptyMap(),
    val docker: DockerCommandLineOptions = DockerCommandLineOptions(),
    val cacheType: CacheType = CacheType.Volume,
    val existingNetworkToUse: String? = null,
    val skipPrerequisites: Boolean = false,
    val disableTelemetry: Boolean? = null,
    val enableBuildKit: Boolean? = null,
    val generateShellTabCompletionScript: Shell? = null,
    val generateShellTabCompletionTaskInformation: Shell? = null,
    val maximumLevelOfParallelism: Int? = null,
    val cleanCaches: Set<String> = emptySet()
) {
    fun extend(originalKodein: DirectDI): DirectDI = subDI(originalKodein.di) {
        bind<CommandLineOptions>() with instance(this@CommandLineOptions)

        bind<LogSink>() with singleton {
            if (logFileName == null) {
                NullLogSink()
            } else {
                FileLogSink(logFileName, instance(), instance())
            }
        }

        import(rootModule)
    }.direct
}

data class DockerCommandLineOptions(
    val contextName: String = "default",
    val host: String = "tcp://set-to-default-value-in/CommandLineOptionsParser",
    val useTLS: Boolean = false,
    val verifyTLS: Boolean = false,
    val tlsCACertificatePath: Path = Paths.get("set-to-default-value-in", "CommandLineOptionsParser"),
    val tlsCertificatePath: Path = Paths.get("set-to-default-value-in", "CommandLineOptionsParser"),
    val tlsKeyPath: Path = Paths.get("set-to-default-value-in", "CommandLineOptionsParser"),
    val configDirectory: Path = Paths.get("set-to-default-value-in", "CommandLineOptionsParser")
)
