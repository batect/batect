/*
    Copyright 2017-2022 Charles Korn.

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

package batect.config.io.deserializers

import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.withColumn
import batect.testutils.withLineNumber
import batect.testutils.withMessage
import batect.testutils.withPath
import com.charleskorn.kaml.Location
import com.charleskorn.kaml.YamlInput
import com.charleskorn.kaml.YamlPath
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import kotlinx.serialization.encoding.Encoder
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

object DurationSerializerSpec : Spek({
    describe("a duration deserializer") {
        val serializer = DurationSerializer

        describe("parsing durations") {
            // These test cases are all taken from the test cases for Golang's ParseDuration.
            // See: https://golang.org/src/time/time_test.go#L850
            mapOf(
                "0" to Duration.ZERO,
                "5s" to 5.seconds,
                "30s" to 30.seconds,
                "1478s" to 1478.seconds,
                "-5s" to -5.seconds,
                "+5s" to 5.seconds,
                "-0" to Duration.ZERO,
                "+0" to Duration.ZERO,
                "5.0s" to 5.seconds,
                "5.6s" to 5.seconds + 600.milliseconds,
                "5.s" to 5.seconds,
                ".5s" to 500.milliseconds,
                "1.0s" to 1.seconds,
                "1.00s" to 1.seconds,
                "1.004s" to 1.seconds + 4.milliseconds,
                "1.0040s" to 1.seconds + 4.milliseconds,
                "100.00100s" to 100.seconds + 1.milliseconds,
                "10ns" to 10.nanoseconds,
                "11us" to 11.microseconds,
                "12µs" to 12.microseconds, // U+00B5
                "12μs" to 12.microseconds, // U+03BC
                "13ms" to 13.milliseconds,
                "14s" to 14.seconds,
                "15m" to 15.minutes,
                "16h" to 16.hours,
                "3h30m" to 3.hours + 30.minutes,
                "10.5s4m" to 4.minutes + 10.seconds + 500.milliseconds,
                "-2m3.4s" to -2.minutes + -3.seconds + -400.milliseconds,
                "1h2m3s4ms5us6ns" to 1.hours + 2.minutes + 3.seconds + 4.milliseconds + 5.microseconds + 6.nanoseconds,
                "39h9m14.425s" to 39.hours + 9.minutes + 14.seconds + 425.milliseconds,
                "52763797000ns" to 52763797000.nanoseconds,
                "0.100000000000000000000h" to 6.minutes,
                "0.830103483285477580700h" to 49.minutes + 48.seconds + 372539828.nanoseconds
            ).forEach { (input, expectedOutput) ->
                given("the string '$input'") {
                    val decoder = mock<YamlInput> {
                        on { decodeString() } doReturn input
                    }

                    val parsed = serializer.deserialize(decoder)

                    it("returns the expected duration value") {
                        assertThat(parsed, equalTo(expectedOutput))
                    }
                }
            }

            listOf(
                "",
                "3",
                "-",
                "s",
                ".",
                "-.",
                ".s",
                "+.s"
            ).forEach { input ->
                given("the invalid string '$input'") {
                    val decoder = mock<YamlInput> {
                        on { decodeString() } doReturn input
                        on { getCurrentPath() } doReturn YamlPath.root.withListEntry(0, Location(3, 4))
                    }

                    it("throws an appropriate exception") {
                        assertThat(
                            { serializer.deserialize(decoder) },
                            throws(
                                withMessage("The value '$input' is not a valid duration.") and
                                    withLineNumber(3) and
                                    withColumn(4) and
                                    withPath("[0]")
                            )
                        )
                    }
                }
            }
        }

        describe("serializing durations") {
            mapOf(
                Duration.ZERO to "0",
                5.seconds to "5s",
                30.seconds to "30s",
                1478.seconds to "24m 38s",
                -5.seconds to "-5s",
                5.seconds + 600.milliseconds to "5.6s",
                500.milliseconds to "500ms",
                1.seconds to "1s",
                1.seconds + 4.milliseconds to "1.004s",
                100.seconds + 1.milliseconds to "1m 40.001s",
                10.nanoseconds to "10ns",
                11.microseconds to "11us",
                13.milliseconds to "13ms",
                14.seconds to "14s",
                15.minutes to "15m",
                16.hours to "16h",
                3.hours + 30.minutes to "3h 30m",
                4.minutes + 10.seconds + 500.milliseconds to "4m 10.5s",
                -2.minutes + -3.seconds + -400.milliseconds to "-2m 3.4s",
                1.hours + 2.minutes + 3.seconds + 4.milliseconds + 5.microseconds + 6.nanoseconds to "1h 2m 3.004005006s",
                39.hours + 9.minutes + 14.seconds + 425.milliseconds to "1d 15h 9m 14.425s",
                52763797000.nanoseconds to "52.763797s"
            ).forEach { (duration, expectedOutput) ->
                given("the duration $duration") {
                    val encoder = mock<Encoder>()
                    serializer.serialize(encoder, duration)

                    it("writes the expected value") {
                        verify(encoder).encodeString(expectedOutput)
                    }
                }
            }
        }
    }
})
