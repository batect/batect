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

package batect.integrationtests

import batect.config.HealthCheckConfig
import batect.config.PortMapping
import batect.config.VolumeMount
import batect.docker.DockerAPI
import batect.docker.DockerClient
import batect.docker.DockerConnectivityCheckResult
import batect.docker.DockerContainer
import batect.docker.DockerContainerCreationRequest
import batect.docker.DockerHealthCheckResult
import batect.docker.DockerHttpConfig
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.docker.DockerVersionInfoRetrievalResult
import batect.docker.HealthStatus
import batect.docker.UserAndGroup
import batect.docker.build.DockerIgnoreParser
import batect.docker.build.DockerImageBuildContextFactory
import batect.docker.build.DockerfileParser
import batect.docker.pull.DockerRegistryCredentialsConfigurationFile
import batect.docker.pull.DockerRegistryCredentialsProvider
import batect.docker.pull.DockerRegistryDomainResolver
import batect.docker.pull.DockerRegistryIndexResolver
import batect.docker.run.ContainerIOStreamer
import batect.docker.run.ContainerKiller
import batect.docker.run.ContainerTTYManager
import batect.docker.run.ContainerWaiter
import batect.logging.Logger
import batect.os.NativeMethods
import batect.os.ProcessOutput
import batect.os.ProcessRunner
import batect.os.SignalListener
import batect.os.SystemInfo
import batect.testutils.createForGroup
import batect.testutils.runBeforeGroup
import batect.ui.ConsoleDimensions
import batect.ui.ConsoleInfo
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.or
import com.nhaarman.mockitokotlin2.mock
import jnr.posix.POSIXFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object DockerClientIntegrationTest : Spek({
    describe("a Docker client") {
        val testImagePath by createForGroup { Paths.get("./src/integrationTest/resources/test-image/").toAbsolutePath().toString() }

        val logger by createForGroup { mock<Logger>() }
        val processRunner by createForGroup { ProcessRunner(logger) }
        val httpConfig by createForGroup { DockerHttpConfig(OkHttpClient(), DockerHttpConfig.defaultDockerHost) }
        val api by createForGroup { DockerAPI(httpConfig, logger) }
        val posix by createForGroup { POSIXFactory.getNativePOSIX() }
        val nativeMethods by createForGroup { NativeMethods(posix) }
        val consoleInfo by createForGroup { ConsoleInfo(posix, processRunner, logger) }
        val credentialsConfigurationFile by createForGroup { DockerRegistryCredentialsConfigurationFile(FileSystems.getDefault(), processRunner, logger) }
        val credentialsProvider by createForGroup { DockerRegistryCredentialsProvider(DockerRegistryDomainResolver(), DockerRegistryIndexResolver(), credentialsConfigurationFile) }
        val ignoreParser by createForGroup { DockerIgnoreParser() }
        val imageBuildContextFactory by createForGroup { DockerImageBuildContextFactory(ignoreParser) }
        val dockerfileParser by createForGroup { DockerfileParser() }
        val waiter by createForGroup { ContainerWaiter(api) }
        val streamer by createForGroup { ContainerIOStreamer(System.out, System.`in`) }
        val signalListener by createForGroup { SignalListener(posix) }
        val consoleDimensions by createForGroup { ConsoleDimensions(nativeMethods, signalListener, logger) }
        val killer by createForGroup { ContainerKiller(api, signalListener) }
        val ttyManager by createForGroup { ContainerTTYManager(api, consoleInfo, consoleDimensions, logger) }
        val client by createForGroup { DockerClient(api, consoleInfo, credentialsProvider, imageBuildContextFactory, dockerfileParser, waiter, streamer, killer, ttyManager, logger) }

        fun creationRequestForContainer(image: DockerImage, network: DockerNetwork, command: Iterable<String>, volumeMounts: Set<VolumeMount> = emptySet(), portMappings: Set<PortMapping> = emptySet(), userAndGroup: UserAndGroup? = null): DockerContainerCreationRequest {
            return DockerContainerCreationRequest(
                image,
                network,
                command,
                "test-container",
                "test-container",
                emptyMap(),
                null,
                volumeMounts,
                portMappings,
                HealthCheckConfig(),
                userAndGroup,
                false
            )
        }

        fun creationRequestForTestContainer(image: DockerImage, network: DockerNetwork, fileToCreate: Path, command: Iterable<String>): DockerContainerCreationRequest {
            val fileToCreateParent = fileToCreate.parent.toAbsolutePath()
            val volumeMount = VolumeMount(fileToCreateParent.toString(), fileToCreateParent.toString(), null)

            val systemInfo = SystemInfo(posix)
            val userAndGroup = UserAndGroup(systemInfo.userId, systemInfo.groupId)

            return creationRequestForContainer(image, network, command, volumeMounts = setOf(volumeMount), userAndGroup = userAndGroup)
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

        describe("building, creating, starting, stopping and removing a container") {
            val fileToCreate by runBeforeGroup { getRandomTemporaryFilePath() }
            val image by runBeforeGroup { client.build(testImagePath, emptyMap(), setOf("batect-integration-tests-image")) {} }

            beforeGroup {
                withNetwork { network ->
                    withContainer(creationRequestForContainerThatWaits(image, network, fileToCreate)) { container ->
                        client.start(container)
                        client.stop(container)
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
            val image by runBeforeGroup { client.pullImage("alpine:3.7", {}) }

            beforeGroup {
                withNetwork { network ->
                    withContainer(creationRequestForContainerThatWaits(image, network, fileToCreate)) { container ->
                        client.start(container)
                        client.stop(container)
                    }
                }
            }

            it("starts the container successfully") {
                assertThat(Files.exists(fileToCreate), equalTo(true))
                assertThat(Files.readAllLines(fileToCreate), equalTo(listOf("Hello from container")))
            }
        }

        describe("creating, running and then removing a container") {
            val fileToCreate by runBeforeGroup { getRandomTemporaryFilePath() }
            val image by runBeforeGroup { client.pullImage("alpine:3.7", {}) }

            beforeGroup {
                withNetwork { network ->
                    withContainer(creationRequestForContainerThatExits(image, network, fileToCreate)) { container ->
                        client.run(container)
                    }
                }
            }

            it("starts the container successfully") {
                assertThat(Files.exists(fileToCreate), equalTo(true))
                assertThat(Files.readAllLines(fileToCreate), equalTo(listOf("Hello from container")))
            }
        }

        describe("pulling an image that has not been cached locally already") {
            beforeGroup { removeImage("hello-world:latest") }
            val image by runBeforeGroup { client.pullImage("hello-world:latest", {}) }

            it("pulls the image successfully") {
                assertThat(image, equalTo(DockerImage("hello-world:latest")))
            }
        }

        describe("waiting for a container to become healthy") {
            val fileToCreate by runBeforeGroup { getRandomTemporaryFilePath() }
            val image by runBeforeGroup { client.build(testImagePath, emptyMap(), setOf("batect-integration-tests-image")) {} }
            data class Result(val healthStatus: HealthStatus, val lastHealthCheckResult: DockerHealthCheckResult)

            val result by runBeforeGroup {
                withNetwork { network ->
                    withContainer(creationRequestForContainerThatWaits(image, network, fileToCreate)) { container ->
                        try {
                            client.start(container)
                            val healthStatus = client.waitForHealthStatus(container)
                            val lastHealthCheckResult = client.getLastHealthCheckResult(container)
                            Result(healthStatus, lastHealthCheckResult)
                        } finally {
                            client.stop(container)
                        }
                    }
                }
            }

            it("reports that the container became healthy") {
                assertThat(result.healthStatus, equalTo(HealthStatus.BecameHealthy))
            }

            it("reports the result of the last health check") {
                assertThat(result.lastHealthCheckResult, equalTo(DockerHealthCheckResult(0, "Hello from the healthcheck")))
            }
        }

        describe("running a container that exposes a port") {
            describe("given the image has an EXPOSE instruction for the port to be exposed") {
                val image by runBeforeGroup { client.pullImage("nginx:1.15.8", {}) }

                val response by runBeforeGroup {
                    withNetwork { network ->
                        withContainer(creationRequestForContainer(image, network, emptyList(), portMappings = setOf(PortMapping(8080, 80)))) { container ->
                            try {
                                client.start(container)
                                httpGet("http://localhost:8080")
                            } finally {
                                client.stop(container)
                            }
                        }
                    }
                }

                it("successfully starts the container and exposes the port") {
                    assertThat(response, has(Response::code, equalTo(200)))
                }
            }

            describe("given the image does not have an EXPOSE instruction for the port to be exposed") {
                val image by runBeforeGroup { client.pullImage("busybox:1.30.0", {}) }
                val command = listOf("busybox", "httpd", "-f", "-p", "80")

                val response by runBeforeGroup {
                    withNetwork { network ->
                        withContainer(creationRequestForContainer(image, network, command, portMappings = setOf(PortMapping(8080, 80)))) { container ->
                            try {
                                client.start(container)
                                httpGet("http://localhost:8080/.dockerenv")
                            } finally {
                                client.stop(container)
                            }
                        }
                    }
                }

                it("successfully starts the container and exposes the port") {
                    assertThat(response, has(Response::code, equalTo(200)))
                }
            }
        }

        describe("checking if Docker is available") {
            val result by runBeforeGroup { client.checkConnectivity() }

            it("returns that Docker is available") {
                assertThat(result, equalTo<DockerConnectivityCheckResult>(DockerConnectivityCheckResult.Succeeded))
            }
        }

        describe("getting Docker version info") {
            val versionInfoRetrievalResult by runBeforeGroup { client.getDockerVersionInfo() }

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

private fun removeImage(imageName: String) {
    val processRunner = ProcessRunner(mock())
    val result = processRunner.runAndCaptureOutput(listOf("docker", "rmi", "-f", imageName))

    assertThat(result, has(ProcessOutput::output, containsSubstring("No such image: $imageName")) or has(ProcessOutput::exitCode, equalTo(0)))
}

private fun httpGet(url: String): Response {
    retry(3) {
        val client = OkHttpClient.Builder().build()
        val request = Request.Builder()
            .url(url)
            .header("Connection", "close")
            .build()

        val response = client.newCall(request).execute()
        response.close() // We don't use the body.

        return response
    }
}

private inline fun <T> retry(retries: Int, operation: () -> T): T {
    val exceptions = mutableListOf<Throwable>()

    for (retry in 1..retries) {
        try {
            return operation()
        } catch (e: Throwable) {
            exceptions.add(e)
        }
    }

    val exceptionDetails = exceptions
        .mapIndexed { i, e -> "Attempt ${i + 1}: $e\n" }
        .joinToString("\n")

    throw RuntimeException("Could not execute operation after $retries attempts. Exceptions were:\n$exceptionDetails")
}
