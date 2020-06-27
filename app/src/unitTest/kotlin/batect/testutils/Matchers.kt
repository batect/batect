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
import batect.logging.JsonableObject
import batect.logging.LogMessage
import batect.logging.Severity
import batect.ui.text.Text
import batect.ui.text.TextRun
import batect.logging.toDetailedString
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

fun withFileName(fileName: String): Matcher<ConfigurationException> {
    return has(ConfigurationException::fileName, equalTo(fileName))
}

fun hasKeyWithValue(key: String, value: Any?): Matcher<Map<String, Any?>> = object : Matcher.Primitive<Map<String, Any?>>() {
    override fun invoke(actual: Map<String, Any?>): MatchResult {
        if (actual.containsKey(key) && actual.get(key) == value) {
            return MatchResult.Match
        } else {
            return MatchResult.Mismatch("was ${describe(actual)}")
        }
    }

    override val description: String get() = "contains entry with key ${describe(key)} and value ${describe(value)}"
    override val negatedDescription: String get() = "does not contain entry with key ${describe(key)} and value ${describe(value)}"
}

fun hasMessage(messageCriteria: Matcher<LogMessage>): Matcher<InMemoryLogSink> {
    return object : Matcher<InMemoryLogSink> {
        override fun invoke(actual: InMemoryLogSink): MatchResult {
            if (actual.loggedMessages.isEmpty()) {
                return MatchResult.Mismatch("no messages were logged")
            }

            if (actual.loggedMessages.any { messageCriteria(it) == MatchResult.Match }) {
                return MatchResult.Match
            } else {
                val logMessages = actual.loggedMessages.joinToString("\n")
                return MatchResult.Mismatch("none of the logged messaged matched that criteria, messages logged were:\n$logMessages")
            }
        }

        override val description: String get() = "has a log message that ${describe(messageCriteria)}"
        override val negatedDescription: String get() = "does not have a log message that ${describe(messageCriteria)}"
    }
}

fun withLogMessage(message: String): Matcher<LogMessage> = has(LogMessage::message, equalTo(message))
fun withSeverity(severity: Severity): Matcher<LogMessage> = has(LogMessage::severity, equalTo(severity))
fun withException(exception: Throwable): Matcher<LogMessage> = withAdditionalData("exception", exception.toDetailedString())

fun withAdditionalData(key: String, value: Any?): Matcher<LogMessage> = object : Matcher.Primitive<LogMessage>() {
    override fun invoke(actual: LogMessage): MatchResult {
        if (actual.additionalData.containsKey(key) && (actual.additionalData.get(key) as JsonableObject<*>).value == value) {
            return MatchResult.Match
        } else {
            return MatchResult.Mismatch("was ${describe(actual)}")
        }
    }

    override val description: String get() = "contains additional data with key ${describe(key)} and value ${describe(value)}"
    override val negatedDescription: String get() = "does not contain additional data with key ${describe(key)} and value ${describe(value)}"
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
