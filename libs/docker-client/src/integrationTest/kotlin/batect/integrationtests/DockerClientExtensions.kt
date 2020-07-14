/*
   Copyright 2017-2020 Charles Korn.

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

package batect.integrationtests

import batect.docker.DockerContainer
import batect.docker.DockerContainerCreationRequest
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.docker.client.DockerClient
import batect.os.Dimensions
import batect.primitives.CancellationContext
import java.io.ByteArrayInputStream
import java.nio.file.Path
import okio.Sink
import okio.sink
import okio.source

fun <T> DockerClient.withNetwork(action: (DockerNetwork) -> T): T {
    val network = this.networks.create("bridge")

    try {
        return action(network)
    } finally {
        this.networks.delete(network)
    }
}

fun <T> DockerClient.withContainer(creationRequest: DockerContainerCreationRequest, action: (DockerContainer) -> T): T {
    val container = this.containers.create(creationRequest)

    try {
        return action(container)
    } finally {
        this.containers.remove(container)
    }
}

fun DockerClient.pull(imageName: String): DockerImage = retry(3) {
    this.images.pull(imageName, CancellationContext(), {})
}

fun DockerClient.build(imageDirectory: Path, tag: String): DockerImage =
    this.images.build(imageDirectory, emptyMap(), "Dockerfile", setOf(tag), null, CancellationContext()) {}

fun DockerClient.runContainerAndWaitForCompletion(container: DockerContainer, stdout: Sink = System.out.sink(), useTTY: Boolean = true) =
    this.runContainer(container, stdout, useTTY) {}

fun <T : Any> DockerClient.runContainer(container: DockerContainer, stdout: Sink = System.out.sink(), useTTY: Boolean = true, action: () -> T): T {
    lateinit var result: T

    this.containers.run(container, stdout, ByteArrayInputStream(ByteArray(0)).source(), useTTY, CancellationContext(), Dimensions(0, 0)) {
        result = action()
    }

    return result
}
