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

import batect.updates.ZonedDateTimeSerializer
import batect.utils.Version
import batect.utils.toDetailedString
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.internal.BooleanSerializer
import kotlinx.serialization.internal.IntSerializer
import kotlinx.serialization.internal.LongSerializer
import kotlinx.serialization.internal.StringSerializer
import kotlinx.serialization.internal.nullable
import kotlinx.serialization.list
import kotlinx.serialization.map
import java.nio.file.Path
import java.time.ZonedDateTime

class LogMessageBuilder(val severity: Severity, val loggerAdditionalData: Map<String, Jsonable> = emptyMap()) {
    private var message: String = ""
    private val data = HashMap<String, Jsonable>()

    fun message(value: String): LogMessageBuilder {
        message = value
        return this
    }

    fun exception(e: Throwable): LogMessageBuilder = data("exception", e.toDetailedString(), StringSerializer)

    fun <T> data(key: String, value: T, serializer: SerializationStrategy<T>): LogMessageBuilder {
        require(!key.startsWith('@')) { "Cannot add additional data with the key '$key': keys may not start with '@'." }

        data[key] = JsonableObject(value, serializer)
        return this
    }

    fun build(timestampSource: () -> ZonedDateTime, standardAdditionalDataSource: StandardAdditionalDataSource): LogMessage {
        val additionalData = loggerAdditionalData + standardAdditionalDataSource.getAdditionalData() + data

        return LogMessage(severity, message, timestampSource(), additionalData)
    }

    fun data(key: String, value: String) = data(key, value, StringSerializer)
    fun data(key: String, value: Int) = data(key, value, IntSerializer)
    fun data(key: String, value: Long) = data(key, value, LongSerializer)
    fun data(key: String, value: Boolean) = data(key, value, BooleanSerializer)
    fun data(key: String, value: ZonedDateTime) = data(key, value, ZonedDateTimeSerializer)
    fun data(key: String, value: Path) = data(key, value.toString())
    fun data(key: String, value: Version) = data(key, value, Version.Companion)
    fun data(key: String, value: Iterable<String>) = data(key, value.toList(), StringSerializer.list)
    fun data(key: String, value: Map<String, String>) = data(key, value, (StringSerializer to StringSerializer).map)

    @JvmName("nullableData")
    fun data(key: String, value: String?) = data(key, value, StringSerializer.nullable)
}
