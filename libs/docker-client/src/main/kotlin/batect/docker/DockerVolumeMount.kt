/*
    Copyright 2017-2021 Charles Korn.

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

package batect.docker

import kotlinx.serialization.Serializable

@Serializable
data class DockerVolumeMount(val source: DockerVolumeMountSource, val containerPath: String, val options: String? = null) {
    override fun toString(): String = if (options == null) "${source.formatted}:$containerPath" else "${source.formatted}:$containerPath:$options"
}

@Serializable
sealed class DockerVolumeMountSource(val formatted: String) {
    @Serializable
    data class LocalPath(val path: String) : DockerVolumeMountSource(path)

    @Serializable
    data class Volume(val name: String) : DockerVolumeMountSource(name)
}
