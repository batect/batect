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

package batect.docker

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Duration

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = Duration::class)
internal object DockerDurationSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor = Long.serializer().descriptor
    override fun deserialize(decoder: Decoder): Duration = Duration.ofNanos(decoder.decodeLong())
    override fun serialize(encoder: Encoder, value: Duration) = throw UnsupportedOperationException()
}
