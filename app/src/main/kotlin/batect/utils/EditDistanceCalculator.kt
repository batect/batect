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

object EditDistanceCalculator {
    // This is an implementation of the Wagner-Fischer algorithm
    // https://en.wikipedia.org/wiki/Wagner%E2%80%93Fischer_algorithm
    fun calculateDistanceBetween(first: String, second: String): Int {
        val prefixes = Array(first.length + 1) { i ->
            IntArray(second.length + 1) { j ->
                when {
                    i == 0 -> j
                    j == 0 -> i
                    else -> Int.MAX_VALUE
                }
            }
        }

        for (i in 1..first.length) {
            for (j in 1..second.length) {
                prefixes[i][j] = if (first[i - 1] == second[j - 1]) {
                    prefixes[i - 1][j - 1]
                } else {
                    minOf(
                        prefixes[i - 1][j] + 1,
                        prefixes[i][j - 1] + 1,
                        prefixes[i - 1][j - 1] + 1
                    )
                }
            }
        }

        return prefixes[first.length][second.length]
    }
}
