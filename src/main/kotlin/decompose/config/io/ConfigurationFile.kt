package decompose.config.io

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import decompose.config.*

data class ConfigurationFile(
        val projectName: String,
        val tasks: Map<String, TaskFromFile> = emptyMap(),
        val containers: Map<String, ContainerFromFile> = emptyMap()) {

    fun toConfiguration(): Configuration = Configuration(
            projectName,
            tasks.mapValues { (name, task) -> task.toTask(name) },
            containers.mapValues { (name, container) -> container.toContainer(name) })
}

data class TaskFromFile(@JsonProperty("run") val runConfiguration: TaskRunConfiguration,
                        @JsonProperty("start") @JsonDeserialize(using = StringSetDeserializer::class) val dependencies: Set<String> = emptySet()) {

    fun toTask(name: String): Task = Task(name, runConfiguration, dependencies)
}

data class ContainerFromFile(
        val buildDirectory: String,
        @JsonDeserialize(using = EnvironmentDeserializer::class) val environment: Map<String, String> = emptyMap(),
        val workingDirectory: String?,
        @JsonProperty("volumes") val volumeMounts: Set<VolumeMount> = emptySet(),
        @JsonProperty("ports") val portMappings: Set<PortMapping> = emptySet()) {

    fun toContainer(name: String): Container = Container(name, buildDirectory, environment, workingDirectory, volumeMounts, portMappings)
}
