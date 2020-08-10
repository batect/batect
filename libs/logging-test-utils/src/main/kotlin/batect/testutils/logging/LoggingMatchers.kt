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

package batect.testutils.logging

import batect.logging.JsonableObject
import batect.logging.LogMessage
import batect.logging.Severity
import batect.logging.toDetailedString
import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.describe
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has

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
fun withException(exception: Throwable): Matcher<LogMessage> = withException("exception", exception)
fun withException(key: String, exception: Throwable): Matcher<LogMessage> = withAdditionalData(key, exception.toDetailedString())

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

fun withAdditionalDataAndAnyValue(key: String): Matcher<LogMessage> = object : Matcher.Primitive<LogMessage>() {
    override fun invoke(actual: LogMessage): MatchResult {
        if (actual.additionalData.containsKey(key)) {
            return MatchResult.Match
        } else {
            return MatchResult.Mismatch("was ${describe(actual)}")
        }
    }

    override val description: String get() = "contains additional data with key ${describe(key)}"
    override val negatedDescription: String get() = "does not contain additional data with key ${describe(key)}"
}
