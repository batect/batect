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

package batect.config

import batect.config.io.ConfigurationException
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = PortRange.Companion::class)
data class PortRange(val from: Int, val to: Int) {
    constructor(port: Int) : this(port, port)

    init {
        if (from <= 0) {
            throw InvalidPortRangeException("Ports must be positive integers.")
        }

        if (from > to) {
            throw InvalidPortRangeException("Port range limits must be given in ascending order.")
        }
    }

    val size = to - from + 1
    val ports = this.from..this.to

    override fun toString(): String = if (size == 1) { from.toString() } else { "$from-$to" }

    companion object : KSerializer<PortRange> {
        fun parse(value: String): PortRange {
            val regex = """(\d+)(-(\d+))?""".toRegex()

            val match = regex.matchEntire(value)

            if (match == null) {
                throw InvalidPortRangeException("Port range '$value' is invalid. It must be in the form 'port' or 'from-to' and each port must be a positive integer.")
            }

            val from = match.groupValues[1].toInt()
            val to = match.groupValues[3].let { if (it == "") null else it.toInt() }

            try {
                return if (to == null) {
                    PortRange(from)
                } else {
                    PortRange(from, to)
                }
            } catch (e: InvalidPortRangeException) {
                throw InvalidPortRangeException("Port range '$value' is invalid. ${e.message}", e)
            }
        }

        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(PortRange::class.simpleName!!, PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): PortRange {
            val path = (decoder as YamlInput).getCurrentPath()

            try {
                return parse(decoder.decodeString())
            } catch (e: InvalidPortRangeException) {
                throw ConfigurationException(e.message!!, path, e)
            }
        }

        override fun serialize(encoder: Encoder, value: PortRange) = encoder.encodeString(value.toString())
    }
}

class InvalidPortRangeException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
