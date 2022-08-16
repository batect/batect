/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.docker

import batect.os.OperatingSystem
import batect.os.SystemInfo

class DockerHttpConfigDefaults(
    private val systemInfo: SystemInfo
) {
    val defaultDockerHost by lazy {
        when (systemInfo.operatingSystem) {
            OperatingSystem.Mac, OperatingSystem.Linux -> "unix:///var/run/docker.sock"
            OperatingSystem.Windows -> "npipe:////./pipe/docker_engine"
            else -> throw IllegalArgumentException("Unknown operating system.")
        }
    }
}
