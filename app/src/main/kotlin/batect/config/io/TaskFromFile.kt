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

package batect.config.io

import batect.config.Task
import batect.config.TaskRunConfiguration
import batect.config.io.deserializers.DependencySetDeserializer
import batect.config.io.deserializers.PrerequisiteSetDeserializer
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

data class TaskFromFile(
    @JsonProperty("run") val runConfiguration: TaskRunConfiguration,
    val description: String = "",
    @JsonProperty("dependencies") @JsonDeserialize(using = DependencySetDeserializer::class) val dependsOnContainers: Set<String> = emptySet(),
    @JsonProperty("prerequisites") @JsonDeserialize(using = PrerequisiteSetDeserializer::class) val prerequisiteTasks: List<String> = emptyList()
) {
    fun toTask(name: String): Task = Task(name, runConfiguration, description, dependsOnContainers, prerequisiteTasks)
}
