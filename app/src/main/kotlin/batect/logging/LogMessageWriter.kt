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
import batect.utils.Json
import kotlinx.serialization.json.json
import java.io.OutputStream

class LogMessageWriter {
    fun writeTo(message: LogMessage, outputStream: OutputStream) {
        val json = json {
            "@timestamp" to Json.parser.toJson(ZonedDateTimeSerializer, message.timestamp)
            "@message" to message.message
            "@severity" to message.severity.toString().toLowerCase()

            message.additionalData.forEach { (key, value) ->
                key to value.toJSON()
            }
        }

        outputStream.write(json.toString().toByteArray())
        outputStream.write("\n".toByteArray())
    }
}
