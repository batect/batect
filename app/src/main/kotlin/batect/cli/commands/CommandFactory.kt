/*
   Copyright 2017-2018 Charles Korn.

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
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.instance

class CommandFactory {
    fun createCommand(options: CommandLineOptions, kodein: Kodein): Command {
        when {
            options.showHelp -> return kodein.instance<HelpCommand>()
            options.showVersionInfo -> return kodein.instance<VersionInfoCommand>()
            options.listTasks -> return kodein.instance<ListTasksCommand>()
            options.runUpgrade -> return kodein.instance<UpgradeCommand>()
            else -> return kodein.instance<RunTaskCommand>()
        }
    }
}
