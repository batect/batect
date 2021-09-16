/*
    Copyright 2017-2021 Charles Korn.

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
import java.util.concurrent.ConcurrentLinkedQueue

class TelemetrySessionBuilder(
    private val versionInfo: ApplicationVersionInfoProvider,
    private val timeSource: TimeSource = ZonedDateTime::now
) {
    private val sessionId: UUID = UUID.randomUUID()
    private val sessionStartTime: ZonedDateTime = nowInUTC()
    private val applicationId: String = "batect"
    private val applicationVersion: String = versionInfo.version.toString()
    private val attributesBuilder = TelemetryAttributeSetBuilder()
    private val events = ConcurrentLinkedQueue<TelemetryEvent>()
    private val spans = ConcurrentLinkedQueue<TelemetrySpan>()

    fun addAttribute(attributeName: String, value: String?) = attributesBuilder.addAttribute(attributeName, value)
    fun addAttribute(attributeName: String, value: Boolean?) = attributesBuilder.addAttribute(attributeName, value)
    fun addAttribute(attributeName: String, value: Int?) = attributesBuilder.addAttribute(attributeName, value)
    fun addNullAttribute(attributeName: String) = attributesBuilder.addNullAttribute(attributeName)

    fun addEvent(type: String, attributes: Map<String, AttributeValue>) {
        val event = TelemetryEvent(type, nowInUTC(), attributes.mapValues { it.value.json })

        events.add(event)
    }

    fun <R> addSpan(type: String, process: (TelemetrySpanBuilder) -> R): R {
        val startTime = nowInUTC()
        val builder = TelemetrySpanBuilder()

        try {
            return process(builder)
        } finally {
            val endTime = nowInUTC()
            val span = TelemetrySpan(type, startTime, endTime, builder.buildAttributes())

            spans.add(span)
        }
    }

    // Why is TelemetryConfigurationStore passed in here rather than passed in as a constructor parameter?
    // We want to construct this class as early as possible in the application's lifetime - before we've parsed CLI options
    // or anything like that. TelemetryConfigurationStore isn't available until much later, so we have to pass it in.
    fun build(telemetryConfigurationStore: TelemetryConfigurationStore): TelemetrySession {
        val userId = telemetryConfigurationStore.currentConfiguration.userId

        return TelemetrySession(sessionId, userId, sessionStartTime, nowInUTC(), applicationId, applicationVersion, attributesBuilder.build(), events.toList(), spans.toList())
    }

    private fun nowInUTC(): ZonedDateTime = timeSource().withZoneSameInstant(ZoneOffset.UTC)
}

fun TelemetrySessionBuilder.addUnhandledExceptionEvent(e: Throwable, isUserFacing: Boolean) {
    val stackTrace = Thread.currentThread().stackTrace
    val stackFrame = stackTrace.drop(2).first()

    this.addEvent(
        CommonEvents.UnhandledException,
        mapOf(
            CommonAttributes.Exception to AttributeValue(e),
            CommonAttributes.ExceptionCaughtAt to AttributeValue("${stackFrame.className}.${stackFrame.methodName}"),
            CommonAttributes.IsUserFacingException to AttributeValue(isUserFacing)
        )
    )
}
