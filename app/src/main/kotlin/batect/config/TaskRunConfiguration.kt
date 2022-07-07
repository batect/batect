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

@file:UseSerializers(
    CommandSerializer::class
)

package batect.config

import batect.config.io.deserializers.CommandSerializer
import batect.config.io.deserializers.EnvironmentSerializer
import batect.os.Command
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
data class TaskRunConfiguration(
    val container: String,
    val command: Command? = null,
    val entrypoint: Command? = null,
    @SerialName("environment") @Serializable(with = EnvironmentSerializer::class)
    val additionalEnvironmentVariables: Map<String, Expression> = emptyMap(),
    @SerialName("ports") @Serializable(with = PortMappingConfigSetSerializer::class)
    val additionalPortMappings: Set<PortMapping> = emptySet(),
    @SerialName("working_directory") val workingDiretory: String? = null
)
