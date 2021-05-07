/*
   Copyright 2017-2021 Charles Korn.

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

import batect.testutils.equalTo
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.matches
import com.natpryce.hamkrest.startsWith
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object AttributeValueSpec : Spek({
    describe("a telemetry attribute value") {
        describe("including an exception as an attribute") {
            val tab = '\t'

            describe("an exception without a cause") {
                val exception = RuntimeException("Something went wrong.")
                val attribute = AttributeValue(exception)

                it("includes the type of exception and stack trace, but not the exception message") {
                    assertThat(
                        attribute.json.content,
                        startsWith(
                            """
                                |java.lang.RuntimeException
                                |${tab}at batect.telemetry.AttributeValueSpec${'$'}1${'$'}1${'$'}1${'$'}1.invoke(AttributeValueSpec.kt:32)
                                |${tab}at batect.telemetry.AttributeValueSpec${'$'}1${'$'}1${'$'}1${'$'}1.invoke(AttributeValueSpec.kt:31)
                            """.trimMargin()
                        )
                    )
                }
            }

            describe("an exception with a cause") {
                val cause = IllegalArgumentException("The value isn't permitted.")
                val exception = RuntimeException("Something went wrong.", cause)
                val attribute = AttributeValue(exception)

                it("includes the type of outer exception and its stack trace, but not the message") {
                    assertThat(
                        attribute.json.content,
                        startsWith(
                            """
                                |java.lang.RuntimeException
                                |${tab}at batect.telemetry.AttributeValueSpec${'$'}1${'$'}1${'$'}1${'$'}2.invoke(AttributeValueSpec.kt:51)
                                |${tab}at batect.telemetry.AttributeValueSpec${'$'}1${'$'}1${'$'}1${'$'}2.invoke(AttributeValueSpec.kt:49)
                            """.trimMargin()
                        )
                    )
                }

                it("includes the type of the cause and its stack trace, but not the message or the repeated stack frames") {
                    val lines = attribute.json.content.trim().lines().takeLast(3)

                    assertThat(lines[0], equalTo("Caused by: java.lang.IllegalArgumentException"))
                    assertThat(lines[1], equalTo("${tab}at batect.telemetry.AttributeValueSpec${'$'}1${'$'}1${'$'}1${'$'}2.invoke(AttributeValueSpec.kt:50)"))
                    assertThat(lines[2], matches("""^$tab\.\.\. \d+ more$""".toRegex()))
                }
            }

            describe("an exception with a cause that also has a cause") {
                val innerCause = UnsupportedOperationException("Can't do that.")
                val cause = IllegalArgumentException("The value isn't permitted.", innerCause)
                val exception = RuntimeException("Something went wrong.", cause)
                val attribute = AttributeValue(exception)

                it("includes the type of outer exception and its stack trace, but not the message") {
                    assertThat(
                        attribute.json.content,
                        startsWith(
                            """
                                |java.lang.RuntimeException
                                |${tab}at batect.telemetry.AttributeValueSpec${'$'}1${'$'}1${'$'}1${'$'}3.invoke(AttributeValueSpec.kt:79)
                                |${tab}at batect.telemetry.AttributeValueSpec${'$'}1${'$'}1${'$'}1${'$'}3.invoke(AttributeValueSpec.kt:76)
                            """.trimMargin()
                        )
                    )
                }

                it("includes the type of both causes and their stack traces, but not the message or the repeated stack frames") {
                    val lines = attribute.json.content.trim().lines().takeLast(6)

                    assertThat(lines[0], equalTo("Caused by: java.lang.IllegalArgumentException"))
                    assertThat(lines[1], equalTo("${tab}at batect.telemetry.AttributeValueSpec${'$'}1${'$'}1${'$'}1${'$'}3.invoke(AttributeValueSpec.kt:78)"))
                    assertThat(lines[2], matches("""^$tab\.\.\. \d+ more$""".toRegex()))
                    assertThat(lines[3], equalTo("Caused by: java.lang.UnsupportedOperationException"))
                    assertThat(lines[4], equalTo("${tab}at batect.telemetry.AttributeValueSpec${'$'}1${'$'}1${'$'}1${'$'}3.invoke(AttributeValueSpec.kt:77)"))
                    assertThat(lines[5], matches("""^$tab\.\.\. \d+ more$""".toRegex()))
                }
            }
        }
    }
})
