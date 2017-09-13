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

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import batect.cli.commands.ListTasksCommandDefinition
import batect.cli.commands.RunTaskCommandDefinition
import batect.cli.commands.VersionInfoCommandDefinition

class BatectCommandLineParser(kodein: Kodein) : CommandLineParser(kodein) {
    val configurationFileName: String by valueOption("config-file", "The configuration file to use.", "batect.yml", 'f')

    init {
        addCommandDefinition(RunTaskCommandDefinition())
        addCommandDefinition(ListTasksCommandDefinition())
        addCommandDefinition(VersionInfoCommandDefinition())
    }

    override fun createBindings(): Kodein.Module {
        return Kodein.Module {
            bind<String>(CommonOptions.ConfigurationFileName) with instance(configurationFileName)
        }
    }
}
