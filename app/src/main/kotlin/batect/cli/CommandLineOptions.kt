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

package batect.cli

import batect.cli.options.LevelOfParallelismDefaultValueProvider
import batect.logging.FileLogSink
import batect.logging.LogSink
import batect.logging.NullLogSink
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.github.salomonbrys.kodein.singleton
import java.nio.file.FileSystem

data class CommandLineOptions(
    val showHelp: Boolean = false,
    val showVersionInfo: Boolean = false,
    val listTasks: Boolean = false,
    val configurationFileName: String = "batect.yml",
    val logFileName: String? = null,
    val forceSimpleOutputMode: Boolean = false,
    val forceQuietOutputMode: Boolean = false,
    val disableColorOutput: Boolean = false,
    val disableUpdateNotification: Boolean = false,
    val levelOfParallelism: Int = LevelOfParallelismDefaultValueProvider.value,
    val disableCleanupAfterFailure: Boolean = false,
    val dontPropagateProxyEnvironmentVariables: Boolean = false,
    val taskName: String? = null,
    val additionalTaskCommandArguments: Iterable<String> = emptyList()
) {
    fun extend(originalKodein: Kodein): Kodein = Kodein {
        extend(originalKodein)
        bind<CommandLineOptions>() with instance(this@CommandLineOptions)

        bind<LogSink>() with singleton {
            if (logFileName == null) {
                NullLogSink()
            } else {
                val fileSystem = instance<FileSystem>()

                FileLogSink(fileSystem.getPath(logFileName), instance(), instance())
            }
        }

    }
}
