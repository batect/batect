package decompose.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

data class Task(@JsonProperty("run") val runConfiguration: TaskRunConfiguration,
                @JsonProperty("start") @JsonDeserialize(using = StringSetDeserializer::class) val dependencies: Set<String> = emptySet())

data class TaskRunConfiguration(val container: String, val command: String?)
