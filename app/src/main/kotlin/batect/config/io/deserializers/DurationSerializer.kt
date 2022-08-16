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

package batect.config.io.deserializers

import batect.config.io.ConfigurationException
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

object DurationSerializer : KSerializer<Duration> {
    private const val valueRegexString = """((\d+)|(\d+\.\d*)|(\d*\.\d+))(ns|\u00b5s|\u03bcs|us|ms|s|m|h)"""
    private val fullRegex = "^([+-])?(0|($valueRegexString)+)\$".toRegex()
    private val valueRegex = valueRegexString.toRegex()

    override val descriptor: SerialDescriptor = String.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Duration) {
        val formatted = when {
            value == Duration.ZERO -> "0"
            value.isNegative() -> "-" + value.absoluteValue.toString()
            else -> value.toString()
        }

        encoder.encodeString(formatted)
    }

    override fun deserialize(decoder: Decoder): Duration {
        val text = decoder.decodeString()
        val match = fullRegex.matchEntire(text)

        if (match == null) {
            throw ConfigurationException("The value '$text' is not a valid duration.", decoder as YamlInput)
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
            "-" -> duration.unaryMinus()
            else -> throw IllegalArgumentException("Invalid sign: $sign")
        }
    }

    private fun convertValue(value: MatchResult): Duration {
        val numericPart = value.groupValues[1].toDouble()

        return when (val unitPart = value.groupValues[5]) {
            "h" -> numericPart.hours
            "m" -> numericPart.minutes
            "s" -> numericPart.seconds
            "ms" -> numericPart.milliseconds
            "us" -> numericPart.microseconds
            "\u00b5s" -> numericPart.microseconds
            "\u03bcs" -> numericPart.microseconds
            "ns" -> numericPart.nanoseconds
            else -> throw IllegalArgumentException("Unknown time unit: $unitPart")
        }
    }

    private fun Sequence<Duration>.sum(): Duration = this.reduce { acc, item -> acc + item }
}
