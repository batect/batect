/*
   Copyright 2017-2018 Charles Korn.

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

open class DockerException(message: String) : RuntimeException(message)

class ImageBuildFailedException(val outputFromDocker: String) : DockerException("Image build failed. Output from Docker was: $outputFromDocker")
class ContainerCreationFailedException(message: String) : DockerException(message)
class ContainerStartFailedException(val containerId: String, val outputFromDocker: String) : DockerException("Starting container '$containerId' failed: $outputFromDocker")
class ContainerStopFailedException(val containerId: String, val outputFromDocker: String) : DockerException("Stopping container '$containerId' failed: $outputFromDocker")
class ImagePullFailedException(message: String) : DockerException(message)
class ContainerHealthCheckException(message: String) : DockerException(message)
class NetworkCreationFailedException(val outputFromDocker: String) : DockerException("Creation of network failed: $outputFromDocker")
class ContainerRemovalFailedException(val containerId: String, val outputFromDocker: String) : DockerException("Removal of container '$containerId' failed: $outputFromDocker")
class NetworkDeletionFailedException(val networkId: String, val outputFromDocker: String) : DockerException("Deletion of network '$networkId' failed: $outputFromDocker")
