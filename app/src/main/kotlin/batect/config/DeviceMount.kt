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

package batect.config

import batect.config.io.ConfigurationException
import batect.config.io.deserializers.SimpleStringOrObjectSerializer
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceMount(
    @SerialName("local") val localPath: String,
    @SerialName("container") val containerPath: String,
    @SerialName("options") val options: String? = null
) {
    override fun toString(): String {
        return if (options == null) {
            "$localPath:$containerPath"
        } else {
            "$localPath:$containerPath:$options"
        }
    }
}

object DeviceMountConfigSerializer : SimpleStringOrObjectSerializer<DeviceMount>(DeviceMount.serializer()) {
    override val neitherStringNorObjectErrorMessage: String = "Device mount definition is invalid. It must either be an object or a literal in the form 'local_path:container_path' or 'local_path:container_path:options'."

    override fun deserializeFromString(value: String, input: YamlInput): DeviceMount {
        if (value == "") {
            throw ConfigurationException("Device mount definition cannot be empty.", input.node)
        }

        val regex = """(([a-zA-Z]:\\)?[^:]+):([^:]+)(:([^:]+))?""".toRegex()
        val match = regex.matchEntire(value)

        if (match == null) {
            throw invalidMountDefinitionException(value, input)
        }

        val local = match.groupValues[1]
        val container = match.groupValues[3]
        val options = match.groupValues[5].takeIf { it.isNotEmpty() }

        return DeviceMount(local, container, options)
    }

    private fun invalidMountDefinitionException(value: String, input: YamlInput) =
        ConfigurationException(
            "Device mount definition '$value' is invalid. It must be in the form 'local_path:container_path' or 'local_path:container_path:options'.",
            input.node
        )
}
