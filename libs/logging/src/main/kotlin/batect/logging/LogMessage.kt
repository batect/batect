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

package batect.logging

import java.time.ZonedDateTime
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

data class LogMessage(val severity: Severity, val message: String, val timestamp: ZonedDateTime, val additionalData: Map<String, Jsonable>)

enum class Severity {
    Debug,
    Info,
    Warning,
    Error
}

interface Jsonable {
    fun toJSON(json: Json): JsonElement
}

data class JsonableObject<T>(val value: T, val serializer: SerializationStrategy<T>) : Jsonable {
    override fun toJSON(json: Json): JsonElement = json.encodeToJsonElement(serializer, value)
}
