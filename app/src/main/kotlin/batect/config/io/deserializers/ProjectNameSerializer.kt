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
import batect.docker.DockerImageNameValidator
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PrimitiveDescriptor
import kotlinx.serialization.PrimitiveKind
import kotlinx.serialization.SerialDescriptor

object ProjectNameSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveDescriptor("project_name", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val projectName = decoder.decodeString()

        if (!DockerImageNameValidator.isValidImageName(projectName)) {
            val location = (decoder as YamlInput).getCurrentLocation()

            throw ConfigurationException(
                "Invalid project name '$projectName'. The project name must be a valid Docker reference: it ${DockerImageNameValidator.validNameDescription}.",
                location.line,
                location.column
            )
        }

        return projectName
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}
