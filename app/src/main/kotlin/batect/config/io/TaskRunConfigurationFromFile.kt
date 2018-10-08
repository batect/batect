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

import batect.config.EnvironmentVariableExpression
import batect.config.PortMapping
import batect.config.TaskRunConfiguration
import batect.config.io.deserializers.EnvironmentDeserializer
import batect.os.Command
import batect.os.InvalidCommandLineException
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

data class TaskRunConfigurationFromFile(
    val container: String,
    val command: String? = null,
    @JsonDeserialize(using = EnvironmentDeserializer::class) @JsonProperty("environment") val additionalEnvironmentVariables: Map<String, EnvironmentVariableExpression> = emptyMap(),
    @JsonProperty("ports") val additionalPortMappings: Set<PortMapping> = emptySet()
) {
    fun toRunConfiguration(taskName: String): TaskRunConfiguration {
        try {
            val parsedCommand = Command.parse(command)

            return TaskRunConfiguration(container, parsedCommand, additionalEnvironmentVariables, additionalPortMappings)
        } catch (e: InvalidCommandLineException) {
            throw ConfigurationException("Command for task '$taskName' is invalid: ${e.message}")
        }
    }
}
