/*
   Copyright 2017-2019 Charles Korn.

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
import batect.config.Container
import batect.config.EnvironmentVariableExpression
import batect.config.HealthCheckConfig
import batect.config.ImageSource
import batect.config.PortMapping
import batect.config.PullImage
import batect.config.RunAsCurrentUserConfig
import batect.config.VolumeMount
import batect.config.io.deserializers.DependencySetDeserializer
import batect.config.io.deserializers.EnvironmentDeserializer
import batect.os.Command
import batect.os.PathResolutionResult
import batect.os.PathResolver
import batect.os.PathType
import kotlinx.serialization.Optional
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContainerFromFile(
    @SerialName("build_directory") @Optional val buildDirectory: String? = null,
    @SerialName("image") @Optional val imageName: String? = null,
    @Optional val command: Command? = null,
    @Serializable(with = EnvironmentDeserializer::class) @Optional val environment: Map<String, EnvironmentVariableExpression> = emptyMap(),
    @SerialName("working_directory") @Optional val workingDirectory: String? = null,
    @SerialName("volumes") @Optional val volumeMounts: Set<VolumeMount> = emptySet(),
    @SerialName("ports") @Optional val portMappings: Set<PortMapping> = emptySet(),
    @Serializable(with = DependencySetDeserializer::class) @Optional val dependencies: Set<String> = emptySet(),
    @SerialName("health_check") @Optional val healthCheckConfig: HealthCheckConfig = HealthCheckConfig(),
    @SerialName("run_as_current_user") @Optional @Serializable(with = RunAsCurrentUserConfig.Companion::class) val runAsCurrentUserConfig: RunAsCurrentUserConfig = RunAsCurrentUserConfig.RunAsDefaultContainerUser
) {

    fun toContainer(name: String, pathResolver: PathResolver): Container {
        val imageSource = resolveImageSource(name, pathResolver)

        return Container(name, imageSource, command, environment, workingDirectory, volumeMounts, portMappings, dependencies, healthCheckConfig, runAsCurrentUserConfig)
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
        when (val result = pathResolver.resolve(buildDirectory)) {
            is PathResolutionResult.Resolved -> when (result.pathType) {
                PathType.Directory -> return result.absolutePath.toString()
                PathType.DoesNotExist -> throw ConfigurationException("Build directory '$buildDirectory' (resolved to '${result.absolutePath}') for container '$containerName' does not exist.")
                else -> throw ConfigurationException("Build directory '$buildDirectory' (resolved to '${result.absolutePath}') for container '$containerName' is not a directory.")
            }
            is PathResolutionResult.InvalidPath -> throw ConfigurationException("Build directory '$buildDirectory' for container '$containerName' is not a valid path.")
        }
    }
}
