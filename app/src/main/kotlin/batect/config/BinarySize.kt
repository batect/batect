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

package batect.config

import batect.config.io.ConfigurationException
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = BinarySize.Companion::class)
data class BinarySize(
    val bytes: Long
) {
    companion object : KSerializer<BinarySize> {
        fun of(count: Long, unit: BinaryUnit) = BinarySize(count * unit.bytes)

        private val parsingPattern = """^(?<count>\d+)\s*(?<unit>[mkg]?)b?$""".toRegex(RegexOption.IGNORE_CASE)

        override val descriptor: SerialDescriptor = String.serializer().descriptor

        override fun deserialize(decoder: Decoder): BinarySize {
            val value = decoder.decodeString()
            val match = parsingPattern.matchEntire(value)

            if (match == null) {
                throw ConfigurationException("Invalid size '$value'. It must be in the format '123', '123b', '123k', '123m' or '123g'.", decoder as YamlInput)
            }

            val count = match.groups["count"]!!.value.toLong()
            val unit = when (match.groups["unit"]!!.value.toLowerCase()) {
                "" -> BinaryUnit.Byte
                "k" -> BinaryUnit.Kilobyte
                "m" -> BinaryUnit.Megabyte
                "g" -> BinaryUnit.Gigabyte
                else -> throw IllegalArgumentException("Should never happen: unrecognised units in ${BinarySize::class.simpleName}")
            }

            return of(count, unit)
        }

        override fun serialize(encoder: Encoder, value: BinarySize) {
            encoder.encodeLong(value.bytes)
        }
    }
}

enum class BinaryUnit(val bytes: Long) {
    Byte(1),
    Kilobyte(1024),
    Megabyte(1024 * Kilobyte.bytes),
    Gigabyte(1024 * Megabyte.bytes)
}
