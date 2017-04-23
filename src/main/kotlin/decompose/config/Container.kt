package decompose.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

data class Container(
        val buildDirectory: String,
        @JsonDeserialize(using = EnvironmentDeserializer::class) val environment: Map<String, String> = emptyMap(),
        val workingDirectory: String?,
        @JsonProperty("volumes") val volumeMounts: Set<VolumeMount> = emptySet(),
        @JsonProperty("ports") val portMappings: Set<PortMapping> = emptySet())
