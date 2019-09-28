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
object DurationSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor = StringDescriptor

    private const val valueRegexString = """((\d+)|(\d+\.\d*)|(\d*\.\d+))(ns|\u00b5s|\u03bcs|us|ms|s|m|h)"""
    private val fullRegex = "^([+-])?(0|($valueRegexString)+)\$".toRegex()
    private val valueRegex = valueRegexString.toRegex()

    override fun deserialize(decoder: Decoder): Duration {
        val text = decoder.decodeString()
        val match = fullRegex.matchEntire(text)

        if (match == null) {
            val location = (decoder as YamlInput).getCurrentLocation()

            throw ConfigurationException("The value '$text' is not a valid duration.", location.line, location.column)
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

    override fun serialize(encoder: Encoder, obj: Duration) {
        val builder = StringBuilder()
        val absoluteDuration = obj.abs()
        val micros = (absoluteDuration.toNanos() / 1000)

        builder.appendTimeUnit(absoluteDuration.toHours(), "h")
        builder.appendTimeUnit(absoluteDuration.toMinutes() - (absoluteDuration.toHours() * 60), "m")
        builder.appendTimeUnit(absoluteDuration.seconds - (absoluteDuration.toMinutes() * 60), "s")
        builder.appendTimeUnit(absoluteDuration.toMillis() - (absoluteDuration.seconds * 1000), "ms")
        builder.appendTimeUnit(micros - (absoluteDuration.toMillis() * 1000), "us")
        builder.appendTimeUnit(absoluteDuration.toNanos() - (micros * 1000), "ns")

        if (obj.isZero) {
            builder.append("0")
        }

        if (obj.isNegative) {
            builder.insert(0, "-")
        }

        encoder.encodeString(builder.toString())
    }

    private fun StringBuilder.appendTimeUnit(value: Long, abbreviation: String) {
        if (value != 0L) {
            if (this.isNotEmpty()) {
                this.append(" ")
            }

            this.append(value)
            this.append(abbreviation)
        }
    }
}
