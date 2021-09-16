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

package batect.utils

import batect.testutils.equalTo
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object EditDistanceCalculatorSpec : Spek({
    describe("an edit distance calculator") {
        on("calculating the edit distance for two empty strings") {
            val editDistance = EditDistanceCalculator.calculateDistanceBetween("", "")

            it("calculates the edit distance as zero") {
                assertThat(editDistance, equalTo(0))
            }
        }

        on("calculating the edit distance for an empty string and a non-empty string") {
            val editDistance = EditDistanceCalculator.calculateDistanceBetween("", "abc")

            it("calculates the edit distance as the length of the non-empty string") {
                assertThat(editDistance, equalTo(3))
            }
        }

        on("calculating the edit distance for a non-empty string and an empty string") {
            val editDistance = EditDistanceCalculator.calculateDistanceBetween("wxyz", "")

            it("calculates the edit distance as the length of the non-empty string") {
                assertThat(editDistance, equalTo(4))
            }
        }

        on("calculating the edit distance for two identical non-empty strings") {
            val editDistance = EditDistanceCalculator.calculateDistanceBetween("abc", "abc")

            it("calculates the edit distance as zero") {
                assertThat(editDistance, equalTo(0))
            }
        }

        on("calculating the edit distance for two strings differing only by a single character substitution") {
            val editDistance = EditDistanceCalculator.calculateDistanceBetween("abc", "abd")

            it("calculates the edit distance as one") {
                assertThat(editDistance, equalTo(1))
            }
        }

        on("calculating the edit distance for two strings differing only by two character substitutions") {
            val editDistance = EditDistanceCalculator.calculateDistanceBetween("abc", "ade")

            it("calculates the edit distance as two") {
                assertThat(editDistance, equalTo(2))
            }
        }

        on("calculating the edit distance for two strings differing only by a single character addition") {
            val editDistance = EditDistanceCalculator.calculateDistanceBetween("abc", "abcd")

            it("calculates the edit distance as one") {
                assertThat(editDistance, equalTo(1))
            }
        }

        on("calculating the edit distance for two strings differing only by two character additions") {
            val editDistance = EditDistanceCalculator.calculateDistanceBetween("abc", "abcde")

            it("calculates the edit distance as two") {
                assertThat(editDistance, equalTo(2))
            }
        }

        on("calculating the edit distance for two strings differing only by a single character deletion") {
            val editDistance = EditDistanceCalculator.calculateDistanceBetween("abc", "ab")

            it("calculates the edit distance as one") {
                assertThat(editDistance, equalTo(1))
            }
        }

        on("calculating the edit distance for two strings differing only by two character deletions") {
            val editDistance = EditDistanceCalculator.calculateDistanceBetween("abc", "a")

            it("calculates the edit distance as two") {
                assertThat(editDistance, equalTo(2))
            }
        }

        on("calculating the edit distance for two strings differing by a substitution and an addition") {
            val editDistance = EditDistanceCalculator.calculateDistanceBetween("abc", "abde")

            it("calculates the edit distance as two") {
                assertThat(editDistance, equalTo(2))
            }
        }

        on("calculating the edit distance for two strings differing by a substitution and a deletion") {
            val editDistance = EditDistanceCalculator.calculateDistanceBetween("abc", "xabd")

            it("calculates the edit distance as two") {
                assertThat(editDistance, equalTo(2))
            }
        }

        on("calculating the edit distance for two strings differing by an addition and a deletion") {
            val editDistance = EditDistanceCalculator.calculateDistanceBetween("abc", "xab")

            it("calculates the edit distance as two") {
                assertThat(editDistance, equalTo(2))
            }
        }

        on("calculating the edit distance for two completely different strings of the same length") {
            val editDistance = EditDistanceCalculator.calculateDistanceBetween("abc", "xyz")

            it("calculates the edit distance as equal to the length of those strings (ie. substituting each character for the other)") {
                assertThat(editDistance, equalTo(3))
            }
        }

        on("calculating the edit distance for two completely different strings of different lengths") {
            val editDistance = EditDistanceCalculator.calculateDistanceBetween("abcdef", "xyz")

            it("calculates the edit distance as equal to the length of the longer string (ie. substituting each character for the other then adding the remaining characters)") {
                assertThat(editDistance, equalTo(6))
            }
        }
    }
})
