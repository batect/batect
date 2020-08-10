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

import batect.telemetry.ConsentState
import batect.telemetry.ConsentStateStore
import batect.telemetry.TelemetryUploadQueue
import batect.ui.Console

class DisableTelemetryCommand(
    private val consentStateStore: ConsentStateStore,
    private val telemetryUploadQueue: TelemetryUploadQueue,
    private val console: Console
) : Command {
    override fun run(): Int {
        console.println("Disabling telemetry...")
        consentStateStore.saveConsentState(ConsentState.Disabled)

        console.println("Removing any cached telemetry data not yet uploaded...")
        telemetryUploadQueue.getAll().forEach { telemetryUploadQueue.pop(it) }

        console.println("Telemetry successfully disabled.")

        return 0
    }
}
