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

package batect.config.io.deserializers

import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.withColumn
import batect.testutils.withLineNumber
import batect.testutils.withMessage
import com.charleskorn.kaml.Location
import com.charleskorn.kaml.YamlInput
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration
import java.time.temporal.ChronoUnit

object DurationDeserializerSpec : Spek({
    describe("a duration deserializer") {
        val deserializer = DurationDeserializer

        // These test cases are all taken from the test cases for Golang's ParseDuration.
        // See: https://golang.org/src/time/time_test.go#L850
        mapOf(
            "0" to Duration.ZERO,
            "5s" to Duration.ofSeconds(5),
            "30s" to Duration.ofSeconds(30),
            "1478s" to Duration.ofSeconds(1478),
            "-5s" to Duration.ofSeconds(-5),
            "+5s" to Duration.ofSeconds(5),
            "-0" to Duration.ZERO,
            "+0" to Duration.ZERO,
            "5.0s" to Duration.ofSeconds(5),
            "5.6s" to Duration.ofSeconds(5) + Duration.ofMillis(600),
            "5.s" to Duration.ofSeconds(5),
            ".5s" to Duration.ofMillis(500),
            "1.0s" to Duration.ofSeconds(1),
            "1.00s" to Duration.ofSeconds(1),
            "1.004s" to Duration.ofSeconds(1) + Duration.ofMillis(4),
            "1.0040s" to Duration.ofSeconds(1) + Duration.ofMillis(4),
            "100.00100s" to Duration.ofSeconds(100) + Duration.ofMillis(1),
            "10ns" to Duration.ofNanos(10),
            "11us" to Duration.of(11, ChronoUnit.MICROS),
            "12µs" to Duration.of(12, ChronoUnit.MICROS), // U+00B5
            "12μs" to Duration.of(12, ChronoUnit.MICROS), // U+03BC
            "13ms" to Duration.ofMillis(13),
            "14s" to Duration.ofSeconds(14),
            "15m" to Duration.ofMinutes(15),
            "16h" to Duration.ofHours(16),
            "3h30m" to Duration.ofHours(3) + Duration.ofMinutes(30),
            "10.5s4m" to Duration.ofMinutes(4) + Duration.ofSeconds(10) + Duration.ofMillis(500),
            "-2m3.4s" to Duration.ofMinutes(-2) + Duration.ofSeconds(-3) + Duration.ofMillis(-400),
            "1h2m3s4ms5us6ns" to Duration.ofHours(1) + Duration.ofMinutes(2) + Duration.ofSeconds(3) + Duration.ofMillis(4) + Duration.of(5, ChronoUnit.MICROS) + Duration.ofNanos(6),
            "39h9m14.425s" to Duration.ofHours(39) + Duration.ofMinutes(9) + Duration.ofSeconds(14) + Duration.ofMillis(425),
            "52763797000ns" to Duration.ofNanos(52763797000),
            "0.100000000000000000000h" to Duration.ofMinutes(6),
            "0.830103483285477580700h" to Duration.ofMinutes(49) + Duration.ofSeconds(48) + Duration.ofNanos(372539827)
        ).forEach { (input, expectedOutput) ->
            given("the string '$input'") {
                val decoder = mock<YamlInput> {
                    on { decodeString() } doReturn input
                }

                val parsed = deserializer.deserialize(decoder)

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
                    on { getCurrentLocation() } doReturn Location(3, 4)
                }

                it("throws an appropriate exception") {
                    assertThat({ deserializer.deserialize(decoder) }, throws(
                            withMessage("The value '$input' is not a valid duration.") and
                                withLineNumber(3) and
                                withColumn(4)
                        )
                    )
                }
            }
        }
    }
})
