/*
   Copyright 2017 Charles Korn.

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

import batect.config.BuildImage
import batect.config.Configuration
import batect.config.Container
import batect.config.ContainerMap
import batect.config.HealthCheckConfig
import batect.config.ImageSource
import batect.config.PortMapping
import batect.config.PullImage
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.config.VolumeMount
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

data class ConfigurationFile(
    val projectName: String,
    val tasks: Map<String, TaskFromFile> = emptyMap(),
    val containers: Map<String, ContainerFromFile> = emptyMap()) {

    fun toConfiguration(pathResolver: PathResolver): Configuration = Configuration(
        projectName,
        TaskMap(tasks.map { (name, task) -> task.toTask(name) }),
        ContainerMap(containers.map { (name, container) -> container.toContainer(name, pathResolver) }))
}

data class TaskFromFile(@JsonProperty("run") val runConfiguration: TaskRunConfigurationFromFile,
                        val description: String = "",
                        @JsonProperty("start") @JsonDeserialize(using = StringSetDeserializer::class) val dependsOnContainers: Set<String> = emptySet(),
                        @JsonProperty("prerequisites") @JsonDeserialize(using = StringSetDeserializer::class) val prerequisiteTasks: Set<String> = emptySet()
) {

    fun toTask(name: String): Task = Task(name, runConfiguration.toRunConfiguration(), description, dependsOnContainers, prerequisiteTasks)
}

data class TaskRunConfigurationFromFile(
    val container: String,
    val command: String? = null,
    @JsonDeserialize(using = EnvironmentDeserializer::class) @JsonProperty("environment") val additionalEnvironmentVariables: Map<String, String> = emptyMap()
) {
    fun toRunConfiguration(): TaskRunConfiguration = TaskRunConfiguration(container, command, additionalEnvironmentVariables)
}

data class ContainerFromFile(
    val buildDirectory: String? = null,
    @JsonProperty("image") val imageName: String? = null,
    val command: String? = null,
    @JsonDeserialize(using = EnvironmentDeserializer::class) val environment: Map<String, String> = emptyMap(),
    val workingDirectory: String? = null,
    @JsonProperty("volumes") val volumeMounts: Set<VolumeMount> = emptySet(),
    @JsonProperty("ports") val portMappings: Set<PortMapping> = emptySet(),
    @JsonDeserialize(using = StringSetDeserializer::class) val dependencies: Set<String> = emptySet(),
    @JsonProperty("health_check") val healthCheckConfig: HealthCheckConfig = HealthCheckConfig()) {

    fun toContainer(name: String, pathResolver: PathResolver): Container {
        val imageSource = resolveImageSource(name, pathResolver)

        val resolvedVolumeMounts = volumeMounts.map {
            resolveVolumeMount(it, name, pathResolver)
        }.toSet()

        return Container(name, imageSource, command, environment, workingDirectory, resolvedVolumeMounts, portMappings, dependencies, healthCheckConfig)
    }

    private fun resolveImageSource(containerName: String, pathResolver: PathResolver): ImageSource {
        if (buildDirectory == null && imageName == null) {
            throw ConfigurationException("Container '$containerName' is invalid: either build_directory or image must be specified.")
        }

        if (buildDirectory != null && imageName != null) {
            throw ConfigurationException("Container '$containerName' is invalid: only one of build_directory or image can be specified, but both have been provided.")
        }

        if (buildDirectory != null) {
            return BuildImage(resolveBuildDirectory(containerName, pathResolver, buildDirectory))
        } else {
            return PullImage(imageName!!)
        }
    }

    private fun resolveBuildDirectory(containerName: String, pathResolver: PathResolver, buildDirectory: String): String {
        val result = pathResolver.resolve(buildDirectory)

        when (result) {
            is PathResolutionResult.Resolved -> when (result.pathType) {
                PathType.Directory -> return result.absolutePath
                PathType.DoesNotExist -> throw ConfigurationException("Build directory '$buildDirectory' (resolved to '${result.absolutePath}') for container '$containerName' does not exist.")
                else -> throw ConfigurationException("Build directory '$buildDirectory' (resolved to '${result.absolutePath}') for container '$containerName' is not a directory.")
            }
            is PathResolutionResult.InvalidPath -> throw ConfigurationException("Build directory '$buildDirectory' for container '$containerName' is not a valid path.")
        }
    }

    private fun resolveVolumeMount(volumeMount: VolumeMount, containerName: String, pathResolver: PathResolver): VolumeMount {
        val result = pathResolver.resolve(volumeMount.localPath)

        val resolvedLocalPath = when (result) {
            is PathResolutionResult.Resolved -> result.absolutePath
            is PathResolutionResult.InvalidPath -> throw ConfigurationException("Local path '${volumeMount.localPath}' for volume mount in container '$containerName' is not a valid path.")
        }

        return VolumeMount(resolvedLocalPath, volumeMount.containerPath, volumeMount.options)
    }
}
