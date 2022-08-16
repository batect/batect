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

@file:UseSerializers(
    UUIDSerializer::class,
    ZonedDateTimeSerializer::class
)

package batect.telemetry

import batect.logging.ZonedDateTimeSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonPrimitive
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

@Serializable
data class TelemetrySession(
    val sessionId: UUID,
    val userId: UUID,
    val sessionStartTime: ZonedDateTime,
    val sessionEndTime: ZonedDateTime,
    val applicationId: String,
    val applicationVersion: String,
    val attributes: Map<String, JsonPrimitive>,
    val events: List<TelemetryEvent>,
    val spans: List<TelemetrySpan>
) {
    init {
        if (sessionId.version() != 4) {
            throw InvalidTelemetrySessionException("Session ID must be a v4 (random) UUID.")
        }

        if (userId.version() != 4) {
            throw InvalidTelemetrySessionException("User ID must be a v4 (random) UUID.")
        }

        if (sessionStartTime.zone != ZoneOffset.UTC) {
            throw InvalidTelemetrySessionException("Session start time must be in UTC.")
        }

        if (sessionEndTime.zone != ZoneOffset.UTC) {
            throw InvalidTelemetrySessionException("Session end time must be in UTC.")
        }
    }
}

class InvalidTelemetrySessionException(message: String) : RuntimeException(message)
