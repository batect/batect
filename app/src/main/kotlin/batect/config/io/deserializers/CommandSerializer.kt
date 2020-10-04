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

package batect.config.io.deserializers

import batect.config.io.ConfigurationException
import batect.os.Command
import batect.os.InvalidCommandLineException
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object CommandSerializer : KSerializer<Command> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("command", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Command = try {
        Command.parse(decoder.decodeString())
    } catch (e: InvalidCommandLineException) {
        if (decoder is YamlInput) {
            throw ConfigurationException(e.message ?: "", decoder)
        } else {
            throw e
        }
    }

    override fun serialize(encoder: Encoder, value: Command) = ListSerializer(String.serializer()).serialize(encoder, value.parsedCommand)
}
