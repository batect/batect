/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

@file:Suppress("ktlint:filename")

package batect.testutils

import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.describe

fun <T, R : T> equalTo(expected: T?): Matcher<R?> =
    object : Matcher<R?> {
        override fun invoke(actual: R?): MatchResult = if (actual == expected) {
            MatchResult.Match
        } else {
            MatchResult.Mismatch("was: ${describe(actual)}")
        }

        override val description: String get() = "is equal to ${describe(expected)}"
        override val negatedDescription: String get() = "is not equal to ${describe(expected)}"
    }
