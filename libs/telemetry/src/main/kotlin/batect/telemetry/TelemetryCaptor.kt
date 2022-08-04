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

import java.time.ZoneOffset
import java.time.ZonedDateTime

interface TelemetryCaptor {
    fun addAttribute(attributeName: String, value: String?)
    fun addAttribute(attributeName: String, value: Boolean?)
    fun addAttribute(attributeName: String, value: Int?)
    fun addNullAttribute(attributeName: String)

    fun addEvent(type: String, attributes: Map<String, AttributeValue>)
    fun addSpan(span: TelemetrySpan)
}

fun <R> TelemetryCaptor.addSpan(
    type: String,
    timeSource: TimeSource = ZonedDateTime::now,
    process: (TelemetrySpanBuilder) -> R
): R {
    fun nowInUTC(): ZonedDateTime = timeSource().withZoneSameInstant(ZoneOffset.UTC)

    val startTime = nowInUTC()
    val builder = TelemetrySpanBuilder()

    try {
        return process(builder)
    } finally {
        val endTime = nowInUTC()
        val span = TelemetrySpan(type, startTime, endTime, builder.buildAttributes())

        addSpan(span)
    }
}

fun TelemetryCaptor.addUnhandledExceptionEvent(e: Throwable, isUserFacing: Boolean) {
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
