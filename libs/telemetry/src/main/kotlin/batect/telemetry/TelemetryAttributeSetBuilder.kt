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

import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

class TelemetryAttributeSetBuilder {
    private val attributes = ConcurrentHashMap<String, JsonPrimitive>()

    fun addAttribute(attributeName: String, value: String?) = addAttribute(attributeName, JsonPrimitive(value))
    fun addAttribute(attributeName: String, value: Boolean?) = addAttribute(attributeName, JsonPrimitive(value))
    fun addAttribute(attributeName: String, value: Int?) = addAttribute(attributeName, JsonPrimitive(value))
    fun addNullAttribute(attributeName: String) = addAttribute(attributeName, JsonNull)

    private fun addAttribute(attributeName: String, value: JsonPrimitive) {
        val existingValue = attributes.putIfAbsent(attributeName, value)

        if (existingValue != null) {
            throw IllegalArgumentException("Attribute '$attributeName' already added.")
        }
    }

    fun build(): Map<String, JsonPrimitive> = attributes
}
