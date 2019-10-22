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

package batect.docker.api

import batect.docker.DockerException

class ContainerInspectionFailedException(message: String) : DockerException(message)
class ContainerRemovalFailedException(val containerId: String, val outputFromDocker: String) : DockerException("Removal of container '$containerId' failed: $outputFromDocker")
class ContainerStartFailedException(val containerId: String, val outputFromDocker: String) : DockerException("Starting container '$containerId' failed: $outputFromDocker")
class ContainerStopFailedException(val containerId: String, val outputFromDocker: String) : DockerException("Stopping container '$containerId' failed: $outputFromDocker")
class DockerVersionInfoRetrievalException(message: String) : DockerException(message)
class NetworkCreationFailedException(val outputFromDocker: String) : DockerException("Creation of network failed: $outputFromDocker")
class NetworkDeletionFailedException(val networkId: String, val outputFromDocker: String) : DockerException("Deletion of network '$networkId' failed: $outputFromDocker")
class ContainerStoppedException(message: String) : DockerException(message)
class ExecInstanceInspectionFailedException(message: String) : DockerException(message)
