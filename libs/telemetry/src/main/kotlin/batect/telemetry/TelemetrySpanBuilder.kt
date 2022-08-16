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

package batect.telemetry

import kotlinx.serialization.json.JsonPrimitive

class TelemetrySpanBuilder {
    private val attributesBuilder = TelemetryAttributeSetBuilder()

    fun addAttribute(attributeName: String, value: String?) = attributesBuilder.addAttribute(attributeName, value)
    fun addAttribute(attributeName: String, value: Boolean?) = attributesBuilder.addAttribute(attributeName, value)
    fun addAttribute(attributeName: String, value: Int?) = attributesBuilder.addAttribute(attributeName, value)
    fun addNullAttribute(attributeName: String) = attributesBuilder.addNullAttribute(attributeName)

    fun buildAttributes(): Map<String, JsonPrimitive> = attributesBuilder.build()
}
