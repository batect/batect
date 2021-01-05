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

package batect.ui

import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration

object FormattingSpec : Spek({
    describe("formatting durations") {
        given("a duration of 0s") {
            val duration = Duration.ZERO

            it("formats it in the expected format") {
                assertThat(duration.humanise(), equalTo("0s"))
            }
        }

        given("a duration less than 30s") {
            val duration = Duration.ofMillis(29_600)

            it("formats it with a fractional number of seconds") {
                assertThat(duration.humanise(), equalTo("29.6s"))
            }
        }

        given("a duration of exactly 30s") {
            val duration = Duration.ofSeconds(30)

            it("formats it with a whole number of seconds") {
                assertThat(duration.humanise(), equalTo("30s"))
            }
        }

        given("a duration of 30s 400ms") {
            val duration = Duration.ofMillis(30_400)

            it("formats it with a whole number of seconds, rounding to the nearest whole second") {
                assertThat(duration.humanise(), equalTo("30s"))
            }
        }

        given("a duration of 30s 600ms") {
            val duration = Duration.ofMillis(30_600)

            it("formats it with a whole number of seconds, rounding to the nearest whole second") {
                assertThat(duration.humanise(), equalTo("31s"))
            }
        }

        given("a duration of 59s") {
            val duration = Duration.ofSeconds(59)

            it("formats it as a number of seconds") {
                assertThat(duration.humanise(), equalTo("59s"))
            }
        }

        given("a duration of 60s") {
            val duration = Duration.ofSeconds(60)

            it("formats it as a number of minutes and seconds") {
                assertThat(duration.humanise(), equalTo("1m 0s"))
            }
        }

        given("a duration of 61s") {
            val duration = Duration.ofSeconds(61)

            it("formats it as a number of minutes and seconds") {
                assertThat(duration.humanise(), equalTo("1m 1s"))
            }
        }

        given("a duration of 59m 59s") {
            val duration = Duration.ofMinutes(59).plusSeconds(59)

            it("formats it as a number of minutes and seconds") {
                assertThat(duration.humanise(), equalTo("59m 59s"))
            }
        }

        given("a duration of 60m") {
            val duration = Duration.ofMinutes(60)

            it("formats it as a number of hours, minutes and seconds") {
                assertThat(duration.humanise(), equalTo("1h 0m 0s"))
            }
        }

        given("a duration of 2h 13m 45s") {
            val duration = Duration.ofHours(2).plusMinutes(13).plusSeconds(45)

            it("formats it as a number of hours, minutes and seconds") {
                assertThat(duration.humanise(), equalTo("2h 13m 45s"))
            }
        }
    }
})
