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

import batect.config.io.deserializers.ProjectNameSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConfigurationFile(
    @SerialName("project_name") @Serializable(with = ProjectNameSerializer::class)
    val projectName: String? = null,
    @SerialName("forbid_telemetry") val forbidTelemetry: Boolean = false,
    val tasks: TaskMap = TaskMap(),
    val containers: ContainerMap = ContainerMap(),
    @SerialName("config_variables") val configVariables: ConfigVariableMap = ConfigVariableMap(),
    @SerialName("include") @Serializable(with = IncludeSetConfigSerializer::class)
    val includes: Set<Include> = emptySet()
)
