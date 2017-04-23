package decompose.config

import com.fasterxml.jackson.annotation.JsonProperty

data class Task(@JsonProperty("run") val runConfiguration: TaskRunConfiguration,
                @JsonProperty("start") val dependencies: List<String> = emptyList())

data class TaskRunConfiguration(val container: String, val command: String?)
