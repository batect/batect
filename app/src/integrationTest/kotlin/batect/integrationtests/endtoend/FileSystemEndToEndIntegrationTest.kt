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

package batect.integrationtests.endtoend

import batect.config.VolumeMount
import batect.docker.DockerContainerCreationRequest
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.docker.UserAndGroup
import batect.integrationtests.build
import batect.integrationtests.createClient
import batect.integrationtests.creationRequestForContainer
import batect.integrationtests.getNativeMethodsForPlatform
import batect.integrationtests.runContainerAndWaitForCompletion
import batect.integrationtests.testImagesDirectory
import batect.integrationtests.withContainer
import batect.integrationtests.withNetwork
import batect.os.NativeMethods
import batect.testutils.createForGroup
import batect.testutils.given
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

object FileSystemEndToEndIntegrationTest : Spek({
    describe("using the local filesystem from a Docker container") {
        val posix by createForGroup { POSIXFactory.getNativePOSIX() }
        val nativeMethods by createForGroup { getNativeMethodsForPlatform(posix) }
        val client by createForGroup { createClient(posix, nativeMethods) }

        given("a container that creates a file in a mounted directory") {
            val image by runBeforeGroup { client.build(testImagesDirectory.resolve("basic-image"), "batect-integration-tests-image") }
            val fileToCreate by runBeforeGroup { getRandomTemporaryFilePath() }

            beforeGroup {
                client.withNetwork { network ->
                    client.withContainer(creationRequestForContainerThatCreatesFile(image, network, fileToCreate, nativeMethods)) { container ->
                        client.runContainerAndWaitForCompletion(container)
                    }
                }
            }

            it("writes the file to the local file system") {
                assertThat(Files.exists(fileToCreate), equalTo(true))
                assertThat(Files.readAllLines(fileToCreate), equalTo(listOf("Hello from container")))
            }
        }
    }
})

private fun creationRequestForContainerThatCreatesFile(image: DockerImage, network: DockerNetwork, localFileToCreate: Path, nativeMethods: NativeMethods): DockerContainerCreationRequest {
    val localMountDirectory = localFileToCreate.parent.toAbsolutePath()
    val containerMountDirectory = "/tmp/batect-integration-test"
    val containerFileToCreate = "$containerMountDirectory/${localFileToCreate.fileName}"
    val volumeMount = VolumeMount(localMountDirectory.toString(), containerMountDirectory, null)

    val command = listOf("sh", "-c", "echo 'Hello from container' >> \"$containerFileToCreate\"")
    val userAndGroup = UserAndGroup(getUserId(nativeMethods), getGroupId(nativeMethods))

    return creationRequestForContainer(image, network, command, volumeMounts = setOf(volumeMount), userAndGroup = userAndGroup)
}

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
