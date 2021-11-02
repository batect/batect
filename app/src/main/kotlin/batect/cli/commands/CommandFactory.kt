/*
    Copyright 2017-2021 Charles Korn.

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
import batect.cli.commands.completion.GenerateShellTabCompletionScriptCommand
import batect.cli.commands.completion.GenerateShellTabCompletionTaskInformationCommand
import org.kodein.di.DirectDI
import org.kodein.di.instance

class CommandFactory {
    fun createCommand(options: CommandLineOptions, kodein: DirectDI): Command {
        return when {
            options.showHelp -> kodein.instance<HelpCommand>()
            options.showVersionInfo -> kodein.instance<VersionInfoCommand>()
            options.listTasks -> kodein.instance<ListTasksCommand>()
            options.runUpgrade -> kodein.instance<UpgradeCommand>()
            options.runCleanup || options.cleanCaches.isNotEmpty() -> kodein.instance<CleanupCachesCommand>()
            options.permanentlyDisableTelemetry -> kodein.instance<DisableTelemetryCommand>()
            options.permanentlyEnableTelemetry -> kodein.instance<EnableTelemetryCommand>()
            options.generateShellTabCompletionScript != null -> kodein.instance<GenerateShellTabCompletionScriptCommand>()
            options.generateShellTabCompletionTaskInformation != null -> kodein.instance<GenerateShellTabCompletionTaskInformationCommand>()
            else -> kodein.instance<RunTaskCommand>()
        }
    }
}
