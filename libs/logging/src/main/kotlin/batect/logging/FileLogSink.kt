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

package batect.logging

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.ZoneOffset
import java.time.ZonedDateTime

class FileLogSink(
    val path: Path,
    private val writer: LogMessageWriter,
    private val standardAdditionalDataSource: StandardAdditionalDataSource,
    private val timestampSource: () -> ZonedDateTime,
) : LogSink {
    private val lock = Object()
    private val fileStream = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND)

    constructor(path: Path, writer: LogMessageWriter, standardAdditionalDataSource: StandardAdditionalDataSource) :
        this(path, writer, standardAdditionalDataSource, { ZonedDateTime.now(ZoneOffset.UTC) })

    override fun write(severity: Severity, loggerAdditionalData: Map<String, Jsonable>, build: LogMessageBuilder.() -> Unit) {
        val builder = LogMessageBuilder(severity, loggerAdditionalData)
        build(builder)

        val message = builder.build(timestampSource, standardAdditionalDataSource)

        synchronized(lock) {
            writer.writeTo(message, fileStream)
        }
    }
}
