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

@file:UseSerializers(
    ZonedDateTimeSerializer::class
)

package batect.telemetry

import batect.logging.ZonedDateTimeSerializer
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class TelemetryEvent(
    val type: String,
    val time: ZonedDateTime,
    val attributes: Map<String, JsonPrimitive>
) {
    init {
        if (time.zone != ZoneOffset.UTC) {
            throw InvalidTelemetryEventException("Event time must be in UTC.")
        }
    }
}

class InvalidTelemetryEventException(message: String) : RuntimeException(message)
