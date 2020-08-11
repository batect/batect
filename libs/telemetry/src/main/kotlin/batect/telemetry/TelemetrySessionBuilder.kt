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

import batect.primitives.ApplicationVersionInfoProvider
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

class TelemetrySessionBuilder(
    private val versionInfo: ApplicationVersionInfoProvider,
    private val timeSource: TimeSource = ZonedDateTime::now
) {
    private val sessionId: UUID = UUID.randomUUID()
    private val sessionStartTime: ZonedDateTime = nowInUTC()
    private val applicationId: String = "batect"
    private val applicationVersion: String = versionInfo.version.toString()

    // Why is TelemetryConfigurationStore passed in here rather than passed in as a constructor parameter?
    // We want to construct this class as early as possible in the application's lifetime - before we've parsed CLI options
    // or anything like that. TelemetryConfigurationStore isn't available until much later, so we have to pass it in.
    fun build(telemetryConfigurationStore: TelemetryConfigurationStore): TelemetrySession {
        val userId = telemetryConfigurationStore.currentConfiguration.userId

        return TelemetrySession(sessionId, userId, sessionStartTime, nowInUTC(), applicationId, applicationVersion)
    }

    private fun nowInUTC(): ZonedDateTime = timeSource().withZoneSameInstant(ZoneOffset.UTC)
}
