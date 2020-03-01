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

import batect.cli.CommandLineOptions
import org.kodein.di.DKodein
import org.kodein.di.generic.instance

class CommandFactory {
    fun createCommand(options: CommandLineOptions, kodein: DKodein): Command {
        return when {
            options.showHelp -> kodein.instance<HelpCommand>()
            options.showVersionInfo -> kodein.instance<VersionInfoCommand>()
            options.listTasks -> kodein.instance<ListTasksCommand>()
            options.runUpgrade -> kodein.instance<UpgradeCommand>()
            options.runCleanup -> kodein.instance<CleanupCachesCommand>()
            else -> kodein.instance<RunTaskCommand>()
        }
    }
}
