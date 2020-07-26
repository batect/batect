/*
   Copyright 2017-2020 Charles Korn.

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

package batect.testutils

import batect.config.io.ConfigurationException
import batect.ui.text.Text
import batect.ui.text.TextRun
import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.describe
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has

fun withLineNumber(lineNumber: Int): Matcher<ConfigurationException> {
    return has(ConfigurationException::lineNumber, equalTo(lineNumber))
}

fun withColumn(column: Int): Matcher<ConfigurationException> {
    return has(ConfigurationException::column, equalTo(column))
}

fun <K, V> isEmptyMap() = Matcher(Map<K, V>::isEmpty)

fun equivalentTo(expected: TextRun): Matcher<TextRun> =
    object : Matcher<TextRun> {
        override fun invoke(actual: TextRun): MatchResult = if (actual.simplify() == expected.simplify()) {
            MatchResult.Match
        } else {
            MatchResult.Mismatch("was: ${describe(actual)}")
        }

        override val description: String get() = "equivalent to ${describe(expected)}"
        override val negatedDescription: String get() = "not equivalent to ${describe(expected)}"
    }

fun equivalentTo(expected: Text) = equivalentTo(TextRun(expected))
