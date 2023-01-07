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

package batect.cli.commands

import batect.telemetry.ConsentState
import batect.telemetry.TelemetryConfigurationStore
import batect.ui.Console

class EnableTelemetryCommand(
    private val telemetryConfigurationStore: TelemetryConfigurationStore,
    private val console: Console,
) : Command {
    override fun run(): Int {
        if (telemetryConfigurationStore.currentConfiguration.state == ConsentState.TelemetryAllowed) {
            console.println("Telemetry already enabled.")

            return 0
        }

        val newConfiguration = telemetryConfigurationStore.currentConfiguration.copy(state = ConsentState.TelemetryAllowed)
        telemetryConfigurationStore.saveConfiguration(newConfiguration)

        console.println("Telemetry successfully enabled.")

        return 0
    }
}
