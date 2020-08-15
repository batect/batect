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

package batect.telemetry

import batect.logging.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class TelemetryManager(
    private val telemetryConsent: TelemetryConsent,
    private val telemetryUploadQueue: TelemetryUploadQueue,
    private val telemetryConfigurationStore: TelemetryConfigurationStore,
    private val ciEnvironmentDetector: CIEnvironmentDetector,
    private val abacusClient: AbacusClient,
    private val logger: Logger
) {
    fun finishSession(sessionBuilder: TelemetrySessionBuilder) {
        if (!telemetryConsent.telemetryAllowed) {
            return
        }

        val session = sessionBuilder.build(telemetryConfigurationStore)
        val sessionPath = telemetryUploadQueue.add(session)

        if (ciEnvironmentDetector.detect().suspectRunningOnCI) {
            tryToUploadNow(sessionPath)
        }
    }

    private fun tryToUploadNow(sessionPath: Path) {
        logger.info {
            message("Suspect application is running on CI, trying to quickly upload telemetry session now.")
            data("sessionPath", sessionPath)
        }

        try {
            val bytes = Files.readAllBytes(sessionPath)
            abacusClient.upload(bytes, Duration.ofSeconds(2))
            telemetryUploadQueue.pop(sessionPath)
        } catch (e: Throwable) {
            logger.error {
                message("Immediate upload of telemetry session failed, will be queued to upload in the background next time.")
                data("sessionPath", sessionPath)
                exception(e)
            }
        }
    }
}
