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

package batect.testutils

import batect.logging.Jsonable
import batect.logging.LogMessage
import batect.logging.LogMessageBuilder
import batect.logging.LogMessageWriter
import batect.logging.LogSink
import batect.logging.Logger
import batect.logging.Severity
import batect.logging.StandardAdditionalDataSource
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.dsl.LifecycleAware
import java.io.ByteArrayOutputStream
import java.time.ZoneOffset
import java.time.ZonedDateTime

class InMemoryLogSink : LogSink {
    val loggedMessages = mutableListOf<LogMessage>()
    val output = ByteArrayOutputStream()

    private val lock = Object()
    private val writer = LogMessageWriter()

    private val additionalDataSource = mock<StandardAdditionalDataSource> {
        on { getAdditionalData() } doReturn emptyMap()
    }

    override fun write(severity: Severity, loggerAdditionalData: Map<String, Jsonable>, build: LogMessageBuilder.() -> LogMessageBuilder) {
        val builder = LogMessageBuilder(severity, loggerAdditionalData)
        build(builder)

        val message = builder.build({ ZonedDateTime.now(ZoneOffset.UTC) }, additionalDataSource)

        synchronized(lock) {
            loggedMessages += message
            writer.writeTo(message, output)
        }
    }
}

fun LifecycleAware.createLoggerForEachTest() = createForEachTest { Logger("test logger", InMemoryLogSink()) }
