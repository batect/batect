/*
    Copyright 2017-2022 Charles Korn.

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

import kotlinx.serialization.json.JsonPrimitive
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentLinkedQueue

class TestTelemetryCaptor : TelemetryCaptor {
    private val attributesBuilder = TelemetryAttributeSetBuilder()
    private val events = ConcurrentLinkedQueue<TelemetryEvent>()
    private val spans = ConcurrentLinkedQueue<TelemetrySpan>()

    override fun addAttribute(attributeName: String, value: String?) = attributesBuilder.addAttribute(attributeName, value)
    override fun addAttribute(attributeName: String, value: Boolean?) = attributesBuilder.addAttribute(attributeName, value)
    override fun addAttribute(attributeName: String, value: Int?) = attributesBuilder.addAttribute(attributeName, value)
    override fun addNullAttribute(attributeName: String) = attributesBuilder.addNullAttribute(attributeName)

    override fun addEvent(type: String, attributes: Map<String, AttributeValue>) {
        val event = TelemetryEvent(type, nowInUTC(), attributes.mapValues { it.value.json })

        events.add(event)
    }

    override fun addSpan(span: TelemetrySpan) {
        spans.add(span)
    }

    val allAttributes: Map<String, JsonPrimitive>
        get() = attributesBuilder.build()

    val allEvents: List<TelemetryEvent>
        get() = events.toList()

    val allSpans: List<TelemetrySpan>
        get() = spans.toList()

    private fun nowInUTC(): ZonedDateTime = ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC)
}
