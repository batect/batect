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

package batect.integrationtests

import batect.docker.ContainerCreationRequest
import batect.docker.DockerDeviceMount
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.docker.DockerPortMapping
import batect.docker.DockerVolumeMount
import batect.docker.HealthCheckConfig
import batect.docker.UserAndGroup
import java.util.UUID

fun creationRequestForContainer(
    image: DockerImage,
    network: DockerNetwork,
    command: List<String>,
    volumeMounts: Set<DockerVolumeMount> = emptySet(),
    deviceMounts: Set<DockerDeviceMount> = emptySet(),
    portMappings: Set<DockerPortMapping> = emptySet(),
    userAndGroup: UserAndGroup? = null,
    useTTY: Boolean = true
): ContainerCreationRequest {
    return ContainerCreationRequest(
        "batect-integration-test-" + UUID.randomUUID().toString(),
        image,
        network,
        command,
        emptyList(),
        "test-container",
        setOf("test-container"),
        emptyMap(),
        emptyMap(),
        null,
        volumeMounts,
        deviceMounts,
        portMappings,
        HealthCheckConfig(),
        userAndGroup,
        privileged = false,
        init = false,
        capabilitiesToAdd = emptySet(),
        capabilitiesToDrop = emptySet(),
        useTTY = useTTY,
        attachStdin = true,
        logDriver = "json-file",
        logOptions = emptyMap(),
        null
    )
}

inline fun <T> retry(retries: Int, delayMillisecondsBetweenAttempts: Long = 200, operation: () -> T): T {
    val exceptions = mutableListOf<Throwable>()

    for (retry in 1..retries) {
        try {
            return operation()
        } catch (e: Throwable) {
            exceptions.add(e)

            Thread.sleep(delayMillisecondsBetweenAttempts)
        }
    }

    val exceptionDetails = exceptions
        .mapIndexed { i, e -> "Attempt ${i + 1}: $e\n" }
        .joinToString("\n")

    throw RuntimeException("Could not execute operation after $retries attempts. Exceptions were:\n$exceptionDetails")
}

val runBuildKitTests = System.getProperty("skipBuildKitTests", "false") == "false"
