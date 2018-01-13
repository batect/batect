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

import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasElement
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.time.ZoneOffset
import java.time.ZonedDateTime

object LogMessageBuilderSpec : Spek({
    describe("a log message builder") {
        val timestampToUse = ZonedDateTime.of(2017, 9, 25, 15, 51, 0, 0, ZoneOffset.UTC)
        val timestampSource = { timestampToUse }

        val standardAdditionalDataSource = mock<StandardAdditionalDataSource> {
            on { getAdditionalData() } doReturn mapOf("@something" to 456)
        }

        on("building a log message with no message or additional information") {
            val builder = LogMessageBuilder(Severity.Info)
            val message = builder.build(timestampSource, standardAdditionalDataSource)

            it("returns a log message with the provided severity") {
                assertThat(message.severity, equalTo(Severity.Info))
            }

            it("returns a log message with an empty message") {
                assertThat(message.message, equalTo(""))
            }

            it("returns a log message with only the standard additional data") {
                assertThat(message.additionalData, equalTo(mapOf<String, Any?>(
                    "@something" to 456
                )))
            }

            it("returns a log message with the timestamp from the timestamp provider") {
                assertThat(message.timestamp, equalTo(timestampToUse))
            }
        }

        on("building a log message with a message") {
            val builder = LogMessageBuilder(Severity.Debug)
                .message("Some message")

            val message = builder.build(timestampSource, standardAdditionalDataSource)

            it("returns a log message with the provided severity") {
                assertThat(message.severity, equalTo(Severity.Debug))
            }

            it("returns a log message with the provided message") {
                assertThat(message.message, equalTo("Some message"))
            }

            it("returns a log message with only the standard additional data") {
                assertThat(message.additionalData, equalTo(mapOf<String, Any?>(
                    "@something" to 456
                )))
            }

            it("returns a log message with the timestamp from the timestamp provider") {
                assertThat(message.timestamp, equalTo(timestampToUse))
            }
        }

        on("building a log message with some additional data") {
            val builder = LogMessageBuilder(Severity.Info)
                .data("some-key", 123)
                .data("some-other-data", "value")

            val message = builder.build(timestampSource, standardAdditionalDataSource)

            it("returns a log message with the provided severity") {
                assertThat(message.severity, equalTo(Severity.Info))
            }

            it("returns a log message with an empty message") {
                assertThat(message.message, equalTo(""))
            }

            it("returns a log message with the standard additional data and the user-provided additional data") {
                assertThat(message.additionalData, equalTo(mapOf<String, Any?>(
                    "some-key" to 123,
                    "some-other-data" to "value",
                    "@something" to 456
                )))
            }

            it("returns a log message with the timestamp from the timestamp provider") {
                assertThat(message.timestamp, equalTo(timestampToUse))
            }
        }

        on("building a log message with some logger-provided additional data") {
            val builder = LogMessageBuilder(Severity.Info, mapOf("@source" to "some.class.name"))
                .data("some-key", 123)
                .data("some-other-data", "value")

            val message = builder.build(timestampSource, standardAdditionalDataSource)

            it("returns a log message with the provided severity") {
                assertThat(message.severity, equalTo(Severity.Info))
            }

            it("returns a log message with an empty message") {
                assertThat(message.message, equalTo(""))
            }

            it("returns a log message with the standard additional data, logger-provided additional data and the user-provided additional data") {
                assertThat(message.additionalData, equalTo(mapOf<String, Any?>(
                    "some-key" to 123,
                    "some-other-data" to "value",
                    "@something" to 456,
                    "@source" to "some.class.name"
                )))
            }

            it("returns a log message with the timestamp from the timestamp provider") {
                assertThat(message.timestamp, equalTo(timestampToUse))
            }
        }

        on("building a log message with some additional data with a key that starts with a '@'") {
            val builder = LogMessageBuilder(Severity.Info)

            it("throws an exception when attempting to add that additional data") {
                assertThat({ builder.data("@some-data", "some-value") }, throws(withMessage("Cannot add additional data with the key '@some-data': keys may not start with '@'.")))
            }
        }

        on("building a log message with an exception") {
            val exceptionCause = RuntimeException("Something else went wrong")
            val exception = RuntimeException("Something went wrong", exceptionCause)
            val builder = LogMessageBuilder(Severity.Debug)
                .exception(exception)

            val message = builder.build(timestampSource, standardAdditionalDataSource)

            it("returns a log message with a formatted version of the exception in the additional data") {
                assertThat(message.additionalData.keys, hasElement("exception"))
                assertThat(message.additionalData["exception"] as String, containsSubstring("java.lang.RuntimeException: Something went wrong"))
                assertThat(message.additionalData["exception"] as String, containsSubstring("Caused by: java.lang.RuntimeException: Something else went wrong"))
            }
        }
    }
})
