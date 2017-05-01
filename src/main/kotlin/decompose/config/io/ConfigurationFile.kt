package decompose.config.io

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import decompose.config.*

data class ConfigurationFile(
        val projectName: String,
        val tasks: Map<String, TaskFromFile> = emptyMap(),
        val containers: Map<String, ContainerFromFile> = emptyMap()) {

    fun toConfiguration(pathResolver: PathResolver): Configuration = Configuration(
            projectName,
            TaskMap(tasks.map { (name, task) -> task.toTask(name) }),
            ContainerMap(containers.map { (name, container) -> container.toContainer(name, pathResolver) }))
}

data class TaskFromFile(@JsonProperty("run") val runConfiguration: TaskRunConfiguration,
                        @JsonProperty("start") @JsonDeserialize(using = StringSetDeserializer::class) val dependencies: Set<String> = emptySet()) {

    fun toTask(name: String): Task = Task(name, runConfiguration, dependencies)
}

data class ContainerFromFile(
        val buildDirectory: String,
        @JsonDeserialize(using = EnvironmentDeserializer::class) val environment: Map<String, String> = emptyMap(),
        val workingDirectory: String? = null,
        @JsonProperty("volumes") val volumeMounts: Set<VolumeMount> = emptySet(),
        @JsonProperty("ports") val portMappings: Set<PortMapping> = emptySet()) {

    fun toContainer(name: String, pathResolver: PathResolver): Container {
        val resolvedBuildDirectory = resolveBuildDirectory(name, pathResolver)

        val resolvedVolumeMounts = volumeMounts.map {
            resolveVolumeMount(it, name, pathResolver)
        }.toSet()

        return Container(name, resolvedBuildDirectory, environment, workingDirectory, resolvedVolumeMounts, portMappings)
    }

    private fun resolveBuildDirectory(containerName: String, pathResolver: PathResolver): String {
        val result = pathResolver.resolve(buildDirectory)

        return when (result) {
            is ResolvedToDirectory -> result.path
            is ResolvedToFile -> throw ConfigurationException("Build directory '$buildDirectory' (resolved to '${result.path}') for container '$containerName' is not a directory.")
            is NotFound -> throw ConfigurationException("Build directory '$buildDirectory' (resolved to '${result.path}') for container '$containerName' does not exist.")
            is InvalidPath -> throw ConfigurationException("Build directory '$buildDirectory' for container '$containerName' is not a valid path.")
        }
    }

    private fun resolveVolumeMount(volumeMount: VolumeMount, containerName: String, pathResolver: PathResolver): VolumeMount {
        val result = pathResolver.resolve(volumeMount.localPath)

        val resolvedLocalPath = when (result) {
            is ResolvedToDirectory -> result.path
            is ResolvedToFile -> result.path
            is NotFound -> throw ConfigurationException("Local path '${volumeMount.localPath}' (resolved to '${result.path}') for volume mount in container '$containerName' does not exist.")
            is InvalidPath -> throw ConfigurationException("Local path '${volumeMount.localPath}' for volume mount in container '$containerName' is not a valid path.")
        }

        return VolumeMount(resolvedLocalPath, volumeMount.containerPath)
    }
}
