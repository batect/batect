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

import batect.cli.commands.ListTasksCommandDefinition
import batect.cli.commands.RunTaskCommandDefinition
import batect.cli.commands.VersionInfoCommandDefinition
import batect.logging.FileLogSink
import batect.logging.LogSink
import batect.logging.NullLogSink
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.github.salomonbrys.kodein.singleton
import java.nio.file.FileSystem

class BatectCommandLineParser(kodein: Kodein) : CommandLineParser(kodein, "batect", "For documentation and further information on batect, visit https://github.com/charleskorn/batect.") {
    val configurationFileName: String by valueOption("config-file", "The configuration file to use.", "batect.yml", 'f')
    val logFileName: String? by valueOption("log-file", "Write internal batect logs to file.")
    val forceSimpleOutputMode: Boolean by flagOption("simple-output", "Force simple output (eg. no updating text) from batect. Automatically enabled if your console is detected to not support these features. Does not affect task command output.")
    val disableColorOutput: Boolean by flagOption("no-colors", "Disable colored output from batect. Does not affect task command output. (implies --simple-output)")

    init {
        addCommandDefinition(RunTaskCommandDefinition())
        addCommandDefinition(ListTasksCommandDefinition())
        addCommandDefinition(VersionInfoCommandDefinition())
    }

    override fun createBindings(): Kodein.Module {
        return Kodein.Module {
            bind<String>(CommonOptions.ConfigurationFileName) with instance(configurationFileName)
            bind<Boolean>(CommonOptions.ForceSimpleOutputMode) with instance(forceSimpleOutputMode || disableColorOutput)
            bind<Boolean>(CommonOptions.DisableColorOutput) with instance(disableColorOutput)

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
}
