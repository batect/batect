/*
    Copyright 2017-2022 Charles Korn.

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

package batect.logging

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Duration

open class DurationLoggingSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor = String.serializer().descriptor

    override fun deserialize(decoder: Decoder): Duration {
        throw UnsupportedOperationException()
    }

    override fun serialize(encoder: Encoder, value: Duration) {
        encoder.encodeString(value.toHumanReadableString())
    }

    private fun Duration.toHumanReadableString(): String {
        val builder = StringBuilder()
        val absoluteDuration = this.abs()
        val micros = (absoluteDuration.toNanos() / 1000)

        builder.appendTimeUnit(absoluteDuration.toHours(), "h")
        builder.appendTimeUnit(absoluteDuration.toMinutes() - (absoluteDuration.toHours() * 60), "m")
        builder.appendTimeUnit(absoluteDuration.seconds - (absoluteDuration.toMinutes() * 60), "s")
        builder.appendTimeUnit(absoluteDuration.toMillis() - (absoluteDuration.seconds * 1000), "ms")
        builder.appendTimeUnit(micros - (absoluteDuration.toMillis() * 1000), "us")
        builder.appendTimeUnit(absoluteDuration.toNanos() - (micros * 1000), "ns")

        if (this.isZero) {
            builder.append("0")
        }

        if (this.isNegative) {
            builder.insert(0, "-")
        }

        return builder.toString()
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
