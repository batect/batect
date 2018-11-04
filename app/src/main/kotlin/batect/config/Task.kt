/*
   Copyright 2017-2018 Charles Korn.

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

import batect.config.io.deserializers.EnvironmentDeserializer
import batect.os.Command
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

data class Task(
    val name: String,
    val runConfiguration: TaskRunConfiguration,
    val description: String = "",
    val dependsOnContainers: Set<String> = emptySet(),
    val prerequisiteTasks: List<String> = emptyList()
)

data class TaskRunConfiguration(
    val container: String,
    val command: Command? = null,
    @JsonDeserialize(using = EnvironmentDeserializer::class) @JsonProperty("environment") val additionalEnvironmentVariables: Map<String, EnvironmentVariableExpression> = emptyMap(),
    @JsonProperty("ports") val additionalPortMappings: Set<PortMapping> = emptySet()
)
