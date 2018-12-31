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

import batect.config.io.ConfigurationException
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.Serializer
import kotlinx.serialization.internal.StringDescriptor
import java.time.Duration
import java.time.temporal.ChronoUnit

@Serializer(forClass = Duration::class)
object DurationDeserializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor = StringDescriptor

    private val valueRegexString = """((\d+)|(\d+\.\d*)|(\d*\.\d+))(ns|\u00b5s|\u03bcs|us|ms|s|m|h)"""
    private val fullRegex = "^([+-])?(0|($valueRegexString)+)\$".toRegex()
    private val valueRegex = valueRegexString.toRegex()

    override fun deserialize(input: Decoder): Duration {
        val text = input.decodeString()
        val match = fullRegex.matchEntire(text)

        if (match == null) {
            val location = (input as YamlInput).getCurrentLocation()

            throw ConfigurationException("The value '$text' is not a valid duration.", null, location.line, location.column, null)
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

    override fun serialize(output: Encoder, obj: Duration) = throw UnsupportedOperationException()
}
