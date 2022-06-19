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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// RawConfiguration + any command line or task-specific overrides (eg. replaced images)
@Serializable
data class TaskSpecialisedConfiguration(
    @SerialName("project_name") val projectName: String,
    val tasks: TaskMap = TaskMap(),
    val containers: ContainerMap = ContainerMap(),
    @SerialName("config_variables") val configVariables: ConfigVariableMap = ConfigVariableMap()
)
