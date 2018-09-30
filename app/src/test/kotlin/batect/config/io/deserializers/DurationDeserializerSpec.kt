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

package batect.config.io.deserializers

import batect.testutils.equalTo
import batect.testutils.withOriginalMessage
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.databind.module.SimpleModule
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import java.time.Duration
import java.time.temporal.ChronoUnit

object DurationDeserializerSpec : Spek({
    describe("a duration deserializer") {
        val deserializer = DurationDeserializer()
        val mapper = ObjectMapper()
            .registerModule(object : SimpleModule() {
                init {
                    addDeserializer(Duration::class.java, deserializer)
                }
            })

        val reader = mapper.readerFor(Duration::class.java)

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
        ).forEach { input, expectedOutput ->
            given("the string '$input'") {
                val parsed = reader.readValue<Duration>('"' + input + '"')

                it("returns the expected duration value") {
                    assertThat(parsed, equalTo(expectedOutput))
                }
            }
        }

        given("a non-string value") {
            it("throws an appropriate exception") {
                assertThat({ reader.readValue<Duration>("1") }, throws<MismatchedInputException>(withOriginalMessage("Cannot deserialize duration value from VALUE_NUMBER_INT.")))
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
                it("throws an appropriate exception") {
                    assertThat({ reader.readValue<Duration>('"' + input + '"') }, throws<InvalidFormatException>(
                        withOriginalMessage("Cannot deserialize value of type `java.time.Duration` from String \"$input\": The value '$input' is not a valid duration.")
                    ))
                }
            }
        }
    }
})
