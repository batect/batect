/*
    Copyright 2017-2022 Charles Korn.

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

open class DockerException(message: String, cause: Throwable?) : RuntimeException(message, cause) {
    constructor(message: String) : this(message, null)
}

class ContainerCreationFailedException(message: String, cause: Throwable? = null) : DockerException(message, cause)
class ContainerHealthCheckException(message: String, cause: Throwable? = null) : DockerException(message, cause)
class DockerRegistryCredentialsException(message: String, cause: Throwable? = null) : DockerException(message, cause)
open class InvalidDockerConfigurationException(message: String) : DockerException(message)
class InvalidDockerTLSConfigurationException(message: String) : InvalidDockerConfigurationException(message)
