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

package batect.integrationtests

import batect.config.HealthCheckConfig
import batect.config.VolumeMount
import batect.docker.DockerClient
import batect.docker.DockerContainer
import batect.docker.DockerContainerCreationRequest
import batect.docker.DockerHealthCheckResult
import batect.docker.DockerHttpConfig
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.docker.DockerVersionInfoRetrievalResult
import batect.docker.HealthStatus
import batect.docker.UserAndGroup
import batect.logging.Logger
import batect.os.NativeMethods
import batect.os.ProcessRunner
import batect.os.SystemInfo
import batect.ui.ConsoleInfo
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import com.nhaarman.mockito_kotlin.mock
import jnr.posix.POSIXFactory
import okhttp3.OkHttpClient
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object DockerClientIntegrationTest : Spek({
    describe("a Docker client") {
        val testImagePath = Paths.get("./src/integrationTest/resources/test-image/").toAbsolutePath().toString()

        val logger = mock<Logger>()
        val processRunner = ProcessRunner(logger)
        val httpConfig = DockerHttpConfig(OkHttpClient())
        val posix = POSIXFactory.getNativePOSIX()
        val nativeMethods = NativeMethods(posix)
        val consoleInfo = ConsoleInfo(posix, nativeMethods, logger)
        val systemInfo = SystemInfo(posix)
        val client = DockerClient(processRunner, httpConfig, consoleInfo, logger)

        fun creationRequestForTestContainer(image: DockerImage, network: DockerNetwork, fileToCreate: Path, command: Iterable<String>): DockerContainerCreationRequest {
            val fileToCreateParent = fileToCreate.parent.toAbsolutePath()

            return DockerContainerCreationRequest(
                image,
                network,
                command,
                "test-container",
                "test-container",
                emptyMap(),
                null,
                setOf(VolumeMount(fileToCreateParent.toString(), fileToCreateParent.toString(), null)),
                emptySet(),
                HealthCheckConfig(),
                UserAndGroup(systemInfo.userId, systemInfo.groupId)
            )
        }

        fun creationRequestForContainerThatWaits(image: DockerImage, network: DockerNetwork, fileToCreate: Path): DockerContainerCreationRequest {
            // See https://stackoverflow.com/a/21882119/1668119 for an explanation of this - we need something that waits indefinitely but immediately responds to a SIGTERM by quitting (sh and wait don't do this).
            val command = listOf("sh", "-c", "echo 'Hello from container' >> \"$fileToCreate\"; trap 'trap - TERM; kill -s TERM -$$' TERM; tail -f /dev/null & wait")

            return creationRequestForTestContainer(image, network, fileToCreate, command)
        }

        fun creationRequestForContainerThatExits(image: DockerImage, network: DockerNetwork, fileToCreate: Path): DockerContainerCreationRequest {
            val command = listOf("sh", "-c", "echo 'Hello from container' >> \"$fileToCreate\"")

            return creationRequestForTestContainer(image, network, fileToCreate, command)
        }

        fun <T> withNetwork(action: (DockerNetwork) -> T): T {
            val network = client.createNewBridgeNetwork()

            try {
                return action(network)
            } finally {
                client.deleteNetwork(network)
            }
        }

        fun <T> withContainer(creationRequest: DockerContainerCreationRequest, action: (DockerContainer) -> T): T {
            val container = client.create(creationRequest)

            try {
                return action(container)
            } finally {
                client.remove(container)
            }
        }

        on("building, creating, starting, stopping and removing a container") {
            val fileToCreate = getRandomTemporaryFilePath()
            val image = client.build(testImagePath, emptyMap()) {}

            withNetwork { network ->
                withContainer(creationRequestForContainerThatWaits(image, network, fileToCreate)) { container ->
                    client.start(container)
                    client.stop(container)
                }
            }

            it("starts the container successfully") {
                assertThat(Files.exists(fileToCreate), equalTo(true))
                assertThat(Files.readAllLines(fileToCreate), equalTo(listOf("Hello from container")))
            }
        }

        on("pulling an image and then creating, starting, stopping and removing a container") {
            val fileToCreate = getRandomTemporaryFilePath()
            val image = client.pullImage("alpine:3.7")

            withNetwork { network ->
                withContainer(creationRequestForContainerThatWaits(image, network, fileToCreate)) { container ->
                    client.start(container)
                    client.stop(container)
                }
            }

            it("starts the container successfully") {
                assertThat(Files.exists(fileToCreate), equalTo(true))
                assertThat(Files.readAllLines(fileToCreate), equalTo(listOf("Hello from container")))
            }
        }

        describe("creating, running and then removing a container") {
            val fileToCreate = getRandomTemporaryFilePath()
            val image = client.pullImage("alpine:3.7")

            withNetwork { network ->
                withContainer(creationRequestForContainerThatExits(image, network, fileToCreate)) { container ->
                    client.run(container)
                }
            }

            it("starts the container successfully") {
                assertThat(Files.exists(fileToCreate), equalTo(true))
                assertThat(Files.readAllLines(fileToCreate), equalTo(listOf("Hello from container")))
            }
        }

        describe("waiting for a container to become healthy") {
            val fileToCreate = getRandomTemporaryFilePath()
            val image = client.build(testImagePath, emptyMap()) {}

            val (healthStatus, lastHealthCheckResult) = withNetwork { network ->
                withContainer(creationRequestForContainerThatWaits(image, network, fileToCreate)) { container ->
                    client.start(container)
                    val healthStatus = client.waitForHealthStatus(container)
                    val lastHealthCheckResult = client.getLastHealthCheckResult(container)
                    client.stop(container)

                    Pair(healthStatus, lastHealthCheckResult)
                }
            }

            it("reports that the container became healthy") {
                assertThat(healthStatus, equalTo(HealthStatus.BecameHealthy))
            }

            it("reports the result of the last health check") {
                assertThat(lastHealthCheckResult, equalTo(DockerHealthCheckResult(0, "Hello from the healthcheck")))
            }
        }

        on("checking if Docker is available locally") {
            val isAvailable = client.checkIfDockerIsAvailable()

            it("returns true") {
                assertThat(isAvailable, equalTo(true))
            }
        }

        on("getting Docker version info") {
            val versionInfoRetrievalResult = client.getDockerVersionInfo()

            it("succeeds") {
                assertThat(versionInfoRetrievalResult, isA<DockerVersionInfoRetrievalResult.Succeeded>())
            }
        }
    }
})

private fun getRandomTemporaryFilePath(): Path {
    val temporaryDirectory = Paths.get("./build/tmp/integrationTest/").toAbsolutePath()
    Files.createDirectories(temporaryDirectory)

    val path = Files.createTempFile(temporaryDirectory, "integration-test-", "")
    path.toFile().deleteOnExit()

    return path
}
