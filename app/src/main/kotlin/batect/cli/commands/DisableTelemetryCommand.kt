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
import batect.telemetry.TelemetryConfiguration
import batect.telemetry.TelemetryConfigurationStore
import batect.telemetry.TelemetryUploadQueue
import batect.ui.Console
import java.util.UUID

class DisableTelemetryCommand(
    private val telemetryConfigurationStore: TelemetryConfigurationStore,
    private val telemetryUploadQueue: TelemetryUploadQueue,
    private val console: Console,
) : Command {
    override fun run(): Int {
        console.println("Disabling telemetry and removing user ID...")
        val newConfiguration = TelemetryConfiguration(UUID.randomUUID(), ConsentState.TelemetryDisabled)
        telemetryConfigurationStore.saveConfiguration(newConfiguration)

        console.println("Removing any cached telemetry data not yet uploaded...")
        telemetryUploadQueue.getAll().forEach { telemetryUploadQueue.pop(it) }

        console.println("Telemetry successfully disabled.")

        return 0
    }
}
