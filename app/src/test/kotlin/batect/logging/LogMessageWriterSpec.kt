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

import batect.testutils.CloseableByteArrayOutputStream
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import kotlinx.serialization.json.JsonTreeParser
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.time.ZoneOffset
import java.time.ZonedDateTime

object LogMessageWriterSpec : Spek({
    describe("a log message writer") {
        val writer = LogMessageWriter()
        val messageTime = ZonedDateTime.of(2017, 9, 25, 11, 55, 13, 1234 * 1000, ZoneOffset.UTC)

        on("writing a message with no extra data") {
            val message = LogMessage(Severity.Info, "This is the message", messageTime, emptyMap())
            val output = CloseableByteArrayOutputStream()
            writer.writeTo(message, output)

            val parsed = JsonTreeParser(output.toString()).read().jsonObject

            it("includes the timestamp") {
                assertThat(parsed.getPrimitive("@timestamp").content, equalTo("2017-09-25T11:55:13.001234Z"))
            }

            it("includes the message") {
                assertThat(parsed.getPrimitive("@message").content, equalTo("This is the message"))
            }

            it("includes the severity") {
                assertThat(parsed.getPrimitive("@severity").content, equalTo("info"))
            }

            it("does not include any other fields") {
                assertThat(parsed.keys, equalTo(setOf("@timestamp", "@message", "@severity")))
            }

            it("does not close the output stream") {
                assertThat(output.isClosed, equalTo(false))
            }

            it("appends a new line after the end of the message") {
                assertThat(output.toString().last(), equalTo('\n'))
            }

            it("writes the log message as a single line of text") {
                assertThat(output.toString().trim().lines().size, equalTo(1))
            }
        }

        on("writing a message with extra data") {
            val message = LogMessage(
                Severity.Info,
                "This is the message",
                messageTime,
                mapOf(
                    "some-text" to "This is some text",
                    "some-int" to 123
                )
            )

            val output = CloseableByteArrayOutputStream()
            writer.writeTo(message, output)

            val parsed = JsonTreeParser(output.toString()).read().jsonObject

            it("includes the timestamp") {
                assertThat(parsed.getPrimitive("@timestamp").content, equalTo("2017-09-25T11:55:13.001234Z"))
            }

            it("includes the message") {
                assertThat(parsed.getPrimitive("@message").content, equalTo("This is the message"))
            }

            it("includes the severity") {
                assertThat(parsed.getPrimitive("@severity").content, equalTo("info"))
            }

            it("includes the user-provided fields") {
                assertThat(parsed.getPrimitive("some-text").content, equalTo("This is some text"))
                assertThat(parsed.getPrimitive("some-int").int, equalTo(123))
            }

            it("does not include any other fields") {
                assertThat(parsed.keys, equalTo(setOf("@timestamp", "@message", "@severity", "some-text", "some-int")))
            }

            it("does not close the output stream") {
                assertThat(output.isClosed, equalTo(false))
            }

            it("appends a new line after the end of the message") {
                assertThat(output.toString().last(), equalTo('\n'))
            }

            it("writes the log message as a single line of text") {
                assertThat(output.toString().trim().lines().size, equalTo(1))
            }
        }
    }
})
