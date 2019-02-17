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

package batect.config

import batect.config.io.deserializers.DependencySetDeserializer
import batect.config.io.deserializers.EnvironmentDeserializer
import batect.config.io.deserializers.PrerequisiteListDeserializer
import batect.os.Command
import kotlinx.serialization.Optional
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Task(
    @Transient val name: String = "",
    @SerialName("run") val runConfiguration: TaskRunConfiguration,
    @Optional val description: String = "",
    @SerialName("dependencies") @Serializable(with = DependencySetDeserializer::class) @Optional val dependsOnContainers: Set<String> = emptySet(),
    @SerialName("prerequisites") @Serializable(with = PrerequisiteListDeserializer::class) @Optional val prerequisiteTasks: List<String> = emptyList()
)

@Serializable
data class TaskRunConfiguration(
    val container: String,
    @Optional val command: Command? = null,
    @SerialName("environment") @Serializable(with = EnvironmentDeserializer::class) @Optional val additionalEnvironmentVariables: Map<String, EnvironmentVariableExpression> = emptyMap(),
    @SerialName("ports") @Optional val additionalPortMappings: Set<PortMapping> = emptySet(),
    @Optional @SerialName("working_directory") val workingDiretory: String? = null
)
