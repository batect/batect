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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer
import java.time.Duration
import java.time.temporal.ChronoUnit

class DurationDeserializer : StdScalarDeserializer<Duration>(Duration::class.java) {
    private val valueRegexString = """((\d+)|(\d+\.\d*)|(\d*\.\d+))(ns|\u00b5s|\u03bcs|us|ms|s|m|h)"""
    private val fullRegex = "^([+-])?(0|($valueRegexString)+)\$".toRegex()
    private val valueRegex = valueRegexString.toRegex()

    override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): Duration {
        if (p!!.currentToken != JsonToken.VALUE_STRING) {
            return ctxt!!.handleUnexpectedToken(handledType(), p.currentToken, p, "Cannot deserialize duration value from %s.", p.currentToken) as Duration
        }

        val text = p.text.trim()
        val match = fullRegex.matchEntire(text)

        if (match == null) {
            return ctxt!!.handleWeirdStringValue(handledType(), text, "The value '$text' is not a valid duration.") as Duration
        }

        val sign = match.groupValues[1]
        val value = match.groupValues[2]

        if (value == "0") {
            return Duration.ZERO
        }

        val duration = valueRegex.findAll(value)
            .map { convertValue(it) }
            .sum()

        return when (sign) {
            "" -> duration
            "+" -> duration
            "-" -> duration.negated()
            else -> throw IllegalArgumentException("Invalid sign: $sign")
        }
    }

    private fun convertValue(value: MatchResult): Duration {
        val numericPart = value.groupValues[1].toBigDecimal()

        val unit = when (val unitPart = value.groupValues[5]) {
            "h" -> ChronoUnit.HOURS
            "m" -> ChronoUnit.MINUTES
            "s" -> ChronoUnit.SECONDS
            "ms" -> ChronoUnit.MILLIS
            "us" -> ChronoUnit.MICROS
            "\u00b5s" -> ChronoUnit.MICROS
            "\u03bcs" -> ChronoUnit.MICROS
            "ns" -> ChronoUnit.NANOS
            else -> throw IllegalArgumentException("Unknown time unit: $unitPart")
        }

        return Duration.ofNanos(numericPart.multiply(unit.duration.toNanos().toBigDecimal()).toLong())
    }

    private fun Sequence<Duration>.sum(): Duration = this.reduce { acc, item -> acc + item }
}
