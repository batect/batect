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
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

data class TaskFromFile(@JsonProperty("run") val runConfiguration: TaskRunConfigurationFromFile,
                        val description: String = "",
                        @JsonProperty("dependencies") @JsonAlias("start") @JsonDeserialize(using = StringSetDeserializer::class) val dependsOnContainers: Set<String> = emptySet(),
                        @JsonProperty("prerequisites") @JsonDeserialize(using = StringSetDeserializer::class) val prerequisiteTasks: Set<String> = emptySet()
) {

    fun toTask(name: String): Task = Task(name, runConfiguration.toRunConfiguration(name), description, dependsOnContainers, prerequisiteTasks)
}
