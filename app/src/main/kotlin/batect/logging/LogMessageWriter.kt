/*
   Copyright 2017-2019 Charles Korn.

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

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.OutputStream

class LogMessageWriter {
    private val serializer = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)

    fun writeTo(message: LogMessage, outputStream: OutputStream) {
        val values = mapOf(
            "@timestamp" to message.timestamp,
            "@message" to message.message,
            "@severity" to message.severity.toString().toLowerCase()
        ) + message.additionalData

        serializer.writeValue(outputStream, values)
        outputStream.write("\n".toByteArray())
    }
}
