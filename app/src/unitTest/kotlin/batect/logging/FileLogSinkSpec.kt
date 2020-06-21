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

import batect.testutils.on
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlinx.serialization.builtins.serializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.time.ZoneOffset
import java.time.ZonedDateTime

object FileLogSinkSpec : Spek({
    describe("a file log sink") {
        val fileSystem = Jimfs.newFileSystem(Configuration.unix())
        val path = fileSystem.getPath("/someLogFile.log")
        val writer = mock<LogMessageWriter> {
            on { writeTo(any(), any()) } doAnswer { invocation ->
                val outputStream = invocation.arguments[1] as OutputStream
                PrintStream(outputStream).print("The value written by the writer")

                null
            }
        }

        val standardAdditionalDataSource = mock<StandardAdditionalDataSource> {
            on { getAdditionalData() } doReturn mapOf("someStandardInfo" to JsonableObject(false, Boolean.serializer()))
        }

        val timestampToUse = ZonedDateTime.of(2017, 9, 25, 15, 51, 0, 0, ZoneOffset.UTC)
        val timestampSource = { timestampToUse }

        val sink = FileLogSink(path, writer, standardAdditionalDataSource, timestampSource)

        on("writing a log message") {
            sink.write(Severity.Info, mapOf("someAdditionalInfo" to JsonableObject("someValue", String.serializer()))) {
                message("This is the message")
                data("someLocalInfo", 888)
            }

            val expectedMessage = LogMessage(Severity.Info, "This is the message", timestampToUse, mapOf(
                "someAdditionalInfo" to JsonableObject("someValue", String.serializer()),
                "someLocalInfo" to JsonableObject(888, Int.serializer()),
                "someStandardInfo" to JsonableObject(false, Boolean.serializer())
            ))

            it("calls the builder function to create the log message and passes it to the writer") {
                verify(writer).writeTo(eq(expectedMessage), any())
            }

            it("passes a stream to the writer that writes to the path provided") {
                val content = String(Files.readAllBytes(path))
                assertThat(content, equalTo("The value written by the writer"))
            }
        }
    }
})
