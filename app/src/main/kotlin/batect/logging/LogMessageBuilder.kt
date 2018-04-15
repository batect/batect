/*
   Copyright 2017-2018 Charles Korn.

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

import batect.utils.toDetailedString
import java.time.ZonedDateTime

class LogMessageBuilder(val severity: Severity, val loggerAdditionalData: Map<String, Any> = emptyMap()) {
    private var message: String = ""
    private val data = HashMap<String, Any?>()

    fun message(value: String): LogMessageBuilder {
        message = value
        return this
    }

    fun exception(e: Throwable): LogMessageBuilder = data("exception", e.toDetailedString())

    fun data(key: String, value: Any?): LogMessageBuilder {
        if (key.startsWith('@')) {
            throw IllegalArgumentException("Cannot add additional data with the key '$key': keys may not start with '@'.")
        }

        data[key] = value
        return this
    }

    fun build(timestampSource: () -> ZonedDateTime, standardAdditionalDataSource: StandardAdditionalDataSource): LogMessage {
        val additionalData = loggerAdditionalData + standardAdditionalDataSource.getAdditionalData() + data

        return LogMessage(severity, message, timestampSource(), additionalData)
    }
}
