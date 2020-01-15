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

import batect.config.DeviceMount
import batect.config.HealthCheckConfig
import batect.config.PortMapping
import batect.config.VolumeMount
import batect.docker.DockerContainerCreationRequest
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.docker.UserAndGroup
import batect.execution.CancellationContext
import batect.os.NativeMethods
import batect.testutils.createForGroup
import batect.testutils.runBeforeGroup
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import jnr.ffi.Platform
import jnr.posix.POSIXFactory
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.random.Random

object DockerClientIntegrationTest : Spek({
    describe("a Docker client") {
        val posix by createForGroup { POSIXFactory.getNativePOSIX() }
        val nativeMethods by createForGroup { getNativeMethodsForPlatform(posix) }
        val client by createForGroup { createClient(posix, nativeMethods) }
        val integrationTestImage by runBeforeGroup { client.build(testImagesDirectory.resolve("basic-image"), "batect-integration-tests-image") }

        fun creationRequestForTestContainer(image: DockerImage, network: DockerNetwork, localMountDirectory: Path, containerMountDirectory: String, command: List<String>): DockerContainerCreationRequest {
            val volumeMount = VolumeMount(localMountDirectory.toString(), containerMountDirectory, null)
            val userAndGroup = UserAndGroup(getUserId(nativeMethods), getGroupId(nativeMethods))

            return creationRequestForContainer(image, network, command, volumeMounts = setOf(volumeMount), userAndGroup = userAndGroup)
        }

        fun creationRequestForContainerThatWaits(image: DockerImage, network: DockerNetwork, localFileToCreate: Path): DockerContainerCreationRequest {
            val localMountDirectory = localFileToCreate.parent.toAbsolutePath()
            val containerMountDirectory = "/tmp/batect-integration-test"
            val containerFileToCreate = "$containerMountDirectory/${localFileToCreate.fileName}"

            // See https://stackoverflow.com/a/21882119/1668119 for an explanation of this - we need something that waits indefinitely but immediately responds to a SIGTERM by quitting (sh and wait don't do this).
            val command = listOf("sh", "-c", "echo 'Hello from container' >> \"$containerFileToCreate\"; trap 'trap - TERM; kill -s TERM -$$' TERM; tail -f /dev/null & wait")

            return creationRequestForTestContainer(image, network, localMountDirectory, containerMountDirectory, command)
        }

        fun creationRequestForContainerThatExits(image: DockerImage, network: DockerNetwork, localFileToCreate: Path): DockerContainerCreationRequest {
            val localMountDirectory = localFileToCreate.parent.toAbsolutePath()
            val containerMountDirectory = "/tmp/batect-integration-test"
            val containerFileToCreate = "$containerMountDirectory/${localFileToCreate.fileName}"

            val command = listOf("sh", "-c", "echo 'Hello from container' >> \"$containerFileToCreate\"")

            return creationRequestForTestContainer(image, network, localMountDirectory, containerMountDirectory, command)
        }

        describe("building, creating, starting, stopping and removing a container that does not exit automatically") {
            val fileToCreate by runBeforeGroup { getRandomTemporaryFilePath() }

            beforeGroup {
                client.withNetwork { network ->
                    client.withContainer(creationRequestForContainerThatWaits(integrationTestImage, network, fileToCreate)) { container ->
                        client.runContainerAndStopImmediately(container)
                    }
                }
            }

            it("starts the container successfully") {
                assertThat(Files.exists(fileToCreate), equalTo(true))
                assertThat(Files.readAllLines(fileToCreate), equalTo(listOf("Hello from container")))
            }
        }

        describe("building, creating, starting, stopping and removing a container that exits automatically") {
            val fileToCreate by runBeforeGroup { getRandomTemporaryFilePath() }

            beforeGroup {
                client.withNetwork { network ->
                    client.withContainer(creationRequestForContainerThatExits(integrationTestImage, network, fileToCreate)) { container ->
                        client.runContainerAndWaitForCompletion(container)
                    }
                }
            }

            it("starts the container successfully") {
                assertThat(Files.exists(fileToCreate), equalTo(true))
                assertThat(Files.readAllLines(fileToCreate), equalTo(listOf("Hello from container")))
            }
        }

        describe("pulling an image and then creating, starting, stopping and removing a container") {
            val fileToCreate by runBeforeGroup { getRandomTemporaryFilePath() }
            val image by runBeforeGroup { client.images.pull("alpine:3.7", CancellationContext(), {}) }

            beforeGroup {
                client.withNetwork { network ->
                    client.withContainer(creationRequestForContainerThatWaits(image, network, fileToCreate)) { container ->
                        client.runContainerAndStopImmediately(container)
                    }
                }
            }

            it("starts the container successfully") {
                assertThat(Files.exists(fileToCreate), equalTo(true))
                assertThat(Files.readAllLines(fileToCreate), equalTo(listOf("Hello from container")))
            }
        }
    }
})

private fun getUserId(nativeMethods: NativeMethods): Int = when (Platform.getNativePlatform().os) {
    Platform.OS.WINDOWS -> 0
    else -> nativeMethods.getUserId()
}

private fun getGroupId(nativeMethods: NativeMethods): Int = when (Platform.getNativePlatform().os) {
    Platform.OS.WINDOWS -> 0
    else -> nativeMethods.getGroupId()
}

private fun getRandomTemporaryFilePath(): Path {
    val temporaryDirectory = Paths.get("build", "tmp", "integrationTest").toAbsolutePath()
    Files.createDirectories(temporaryDirectory)

    val path = temporaryDirectory.resolve("integration-test-${Random.nextLong()}")
    Files.deleteIfExists(path)

    return path
}

fun creationRequestForContainer(
    image: DockerImage,
    network: DockerNetwork,
    command: List<String>,
    volumeMounts: Set<VolumeMount> = emptySet(),
    deviceMounts: Set<DeviceMount> = emptySet(),
    portMappings: Set<PortMapping> = emptySet(),
    userAndGroup: UserAndGroup? = null
): DockerContainerCreationRequest {
    return DockerContainerCreationRequest(
        image,
        network,
        command,
        emptyList(),
        "test-container",
        setOf("test-container"),
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
        capabilitiesToDrop = emptySet()
    )
}
