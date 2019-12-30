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

import batect.execution.ConfigVariablesProvider
import batect.logging.FileLogSink
import batect.logging.LogSink
import batect.logging.NullLogSink
import batect.ui.OutputStyle
import org.kodein.di.Copy
import org.kodein.di.DKodein
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.singleton
import java.nio.file.Path
import java.nio.file.Paths

data class CommandLineOptions(
    val showHelp: Boolean = false,
    val showVersionInfo: Boolean = false,
    val runUpgrade: Boolean = false,
    val listTasks: Boolean = false,
    val configurationFileName: Path = Paths.get("batect.yml"),
    val configVariablesSourceFile: Path? = null,
    val logFileName: Path? = null,
    val requestedOutputStyle: OutputStyle? = null,
    val disableColorOutput: Boolean = false,
    val disableUpdateNotification: Boolean = false,
    val disableCleanupAfterFailure: Boolean = false,
    val disableCleanupAfterSuccess: Boolean = false,
    val dontPropagateProxyEnvironmentVariables: Boolean = false,
    val taskName: String? = null,
    val additionalTaskCommandArguments: Iterable<String> = emptyList(),
    val dockerHost: String = "tcp://set-to-default-value-in/CommandLineOptionsParser",
    val dockerUseTLS: Boolean = false,
    val dockerVerifyTLS: Boolean = false,
    val dockerTlsCACertificatePath: Path = Paths.get("set-to-default-value-in", "CommandLineOptionsParser"),
    val dockerTLSCertificatePath: Path = Paths.get("set-to-default-value-in", "CommandLineOptionsParser"),
    val dockerTLSKeyPath: Path = Paths.get("set-to-default-value-in", "CommandLineOptionsParser")
) {
    fun extend(originalKodein: DKodein): DKodein = Kodein.direct {
        extend(originalKodein, copy = Copy.All)
        bind<CommandLineOptions>() with instance(this@CommandLineOptions)
        bind<ConfigVariablesProvider>() with instance(ConfigVariablesProvider(emptyMap(), configVariablesSourceFile))

        bind<LogSink>() with singleton {
            if (logFileName == null) {
                NullLogSink()
            } else {
                FileLogSink(logFileName, instance(), instance())
            }
        }
    }
}
