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

import batect.config.DeviceMount
import batect.config.HealthCheckConfig
import batect.config.PortMapping
import batect.config.VolumeMount
import batect.docker.DockerContainer
import batect.docker.DockerContainerCreationRequest
import batect.docker.DockerExecResult
import batect.docker.DockerHealthCheckResult
import batect.docker.DockerHttpConfig
import batect.docker.DockerHttpConfigDefaults
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.docker.DockerTLSConfig
import batect.docker.UserAndGroup
import batect.docker.api.ContainersAPI
import batect.docker.api.ExecAPI
import batect.docker.api.ImagesAPI
import batect.docker.api.NetworksAPI
import batect.docker.api.SystemInfoAPI
import batect.docker.build.DockerIgnoreParser
import batect.docker.build.DockerImageBuildContextFactory
import batect.docker.build.DockerfileParser
import batect.docker.client.DockerClient
import batect.docker.client.DockerConnectivityCheckResult
import batect.docker.client.DockerContainersClient
import batect.docker.client.DockerExecClient
import batect.docker.client.DockerImagesClient
import batect.docker.client.DockerNetworksClient
import batect.docker.client.DockerSystemInfoClient
import batect.docker.client.DockerVersionInfoRetrievalResult
import batect.docker.client.HealthStatus
import batect.docker.pull.DockerRegistryCredentialsConfigurationFile
import batect.docker.pull.DockerRegistryCredentialsProvider
import batect.docker.pull.DockerRegistryDomainResolver
import batect.docker.pull.DockerRegistryIndexResolver
import batect.docker.run.ContainerIOStreamer
import batect.docker.run.ContainerTTYManager
import batect.docker.run.ContainerWaiter
import batect.execution.CancellationContext
import batect.logging.Logger
import batect.os.Command
import batect.os.ConsoleManager
import batect.os.Dimensions
import batect.os.NativeMethods
import batect.os.ProcessOutput
import batect.os.ProcessRunner
import batect.os.SignalListener
import batect.os.SystemInfo
import batect.os.unix.UnixConsoleManager
import batect.os.unix.UnixNativeMethods
import batect.os.windows.WindowsConsoleManager
import batect.os.windows.WindowsNativeMethods
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
import jnr.ffi.Platform
import jnr.posix.POSIX
import jnr.posix.POSIXFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.sink
import okio.source
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.random.Random

object DockerClientIntegrationTest : Spek({
    describe("a Docker client") {
        val testImagesPath by createForGroup { Paths.get("src", "integrationTest", "resources", "test-images").toAbsolutePath() }
        val basicTestImagePath by createForGroup { testImagesPath.resolve("basic-image") }

        val posix by createForGroup { POSIXFactory.getNativePOSIX() }
        val nativeMethods by createForGroup { getNativeMethodsForPlatform(posix) }
        val client by createForGroup { createClient(posix, nativeMethods) }

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

        fun <T> withNetwork(action: (DockerNetwork) -> T): T {
            val network = client.networks.create()

            try {
                return action(network)
            } finally {
                client.networks.delete(network)
            }
        }

        fun <T> withContainer(creationRequest: DockerContainerCreationRequest, action: (DockerContainer) -> T): T {
            val container = client.containers.create(creationRequest)

            try {
                return action(container)
            } finally {
                client.containers.remove(container)
            }
        }

        describe("building, creating, starting, stopping and removing a container that does not exit automatically") {
            val fileToCreate by runBeforeGroup { getRandomTemporaryFilePath() }
            val image by runBeforeGroup { client.images.build(basicTestImagePath, emptyMap(), "Dockerfile", setOf("batect-integration-tests-image"), null, CancellationContext()) {} }

            beforeGroup {
                withNetwork { network ->
                    withContainer(creationRequestForContainerThatWaits(image, network, fileToCreate)) { container ->
                        client.containers.run(container, System.out.sink(), System.`in`.source(), CancellationContext(), Dimensions(0, 0)) {
                            client.containers.stop(container)
                        }
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
            val image by runBeforeGroup { client.images.build(basicTestImagePath, emptyMap(), "Dockerfile", setOf("batect-integration-tests-image"), null, CancellationContext()) {} }

            beforeGroup {
                withNetwork { network ->
                    withContainer(creationRequestForContainerThatExits(image, network, fileToCreate)) { container ->
                        client.containers.run(container, System.out.sink(), System.`in`.source(), CancellationContext(), Dimensions(0, 0)) {
                            client.containers.stop(container)
                        }
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
                withNetwork { network ->
                    withContainer(creationRequestForContainerThatWaits(image, network, fileToCreate)) { container ->
                        client.containers.run(container, System.out.sink(), System.`in`.source(), CancellationContext(), Dimensions(0, 0)) {
                            client.containers.stop(container)
                        }
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
            val image by runBeforeGroup { client.images.pull("hello-world:latest", CancellationContext(), {}) }

            it("pulls the image successfully") {
                assertThat(image, equalTo(DockerImage("hello-world:latest")))
            }
        }

        describe("waiting for a container to become healthy") {
            val fileToCreate by runBeforeGroup { getRandomTemporaryFilePath() }
            val image by runBeforeGroup { client.images.build(basicTestImagePath, emptyMap(), "Dockerfile", setOf("batect-integration-tests-image"), null, CancellationContext()) {} }
            data class Result(val healthStatus: HealthStatus, val lastHealthCheckResult: DockerHealthCheckResult)

            fun runContainerAndWaitForHealthCheck(container: DockerContainer): Result {
                lateinit var result: Result

                client.containers.run(container, System.out.sink(), System.`in`.source(), CancellationContext(), Dimensions(0, 0)) {
                    val healthStatus = client.containers.waitForHealthStatus(container, CancellationContext())
                    val lastHealthCheckResult = client.containers.getLastHealthCheckResult(container)
                    result = Result(healthStatus, lastHealthCheckResult)

                    client.containers.stop(container)
                }

                return result
            }

            val result by runBeforeGroup {
                withNetwork { network ->
                    withContainer(creationRequestForContainerThatWaits(image, network, fileToCreate)) { container ->
                        runContainerAndWaitForHealthCheck(container)
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
            mapOf(
                "image-with-expose" to "the image has an EXPOSE instruction for the port to be exposed",
                "image-without-expose" to "the image does not have an EXPOSE instruction for the port to be exposed"
            ).forEach { (path, description) ->
                describe("given $description") {
                    val dockerfilePath by createForGroup { testImagesPath.resolve(path) }
                    val image by runBeforeGroup { client.images.build(dockerfilePath, emptyMap(), "Dockerfile", emptySet(), null, CancellationContext(), {}) }

                    fun runContainerAndGetHttpResponse(container: DockerContainer): Response {
                        lateinit var response: Response

                        client.containers.run(container, System.out.sink(), System.`in`.source(), CancellationContext(), Dimensions(0, 0)) {
                            response = httpGet("http://localhost:8080")

                            client.containers.stop(container)
                        }

                        return response
                    }

                    val response by runBeforeGroup {
                        withNetwork { network ->
                            withContainer(creationRequestForContainer(image, network, emptyList(), portMappings = setOf(PortMapping(8080, 80)))) { container ->
                                runContainerAndGetHttpResponse(container)
                            }
                        }
                    }

                    it("successfully starts the container and exposes the port") {
                        assertThat(response, has(Response::code, equalTo(200)))
                    }
                }
            }
        }

        describe("executing a command in a already running container") {
            val image by runBeforeGroup { client.images.pull("alpine:3.7", CancellationContext(), {}) }

            val execResult by runBeforeGroup {
                withNetwork { network ->
                    // See https://stackoverflow.com/a/21882119/1668119 for an explanation of this - we need something that waits indefinitely but immediately responds to a SIGTERM by quitting (sh and wait don't do this).
                    val command = listOf("sh", "-c", "trap 'trap - TERM; kill -s TERM -$$' TERM; tail -f /dev/null & wait")

                    withContainer(creationRequestForContainer(image, network, command)) { container ->
                        lateinit var execResult: DockerExecResult

                        client.containers.run(container, System.out.sink(), System.`in`.source(), CancellationContext(), Dimensions(0, 0)) {
                            execResult = client.exec.run(Command.parse("echo -n 'Output from exec'"), container, emptyMap(), false, null, null, null, CancellationContext())

                            client.containers.stop(container)
                        }

                        execResult
                    }
                }
            }

            it("runs the command successfully") {
                assertThat(execResult.exitCode, equalTo(0))
                assertThat(execResult.output, equalTo("Output from exec"))
            }
        }

        describe("checking if Docker is available") {
            val result by runBeforeGroup { client.systemInfo.checkConnectivity() }

            it("returns that Docker is available") {
                assertThat(result, equalTo<DockerConnectivityCheckResult>(DockerConnectivityCheckResult.Succeeded))
            }
        }

        describe("getting Docker version info") {
            val versionInfoRetrievalResult by runBeforeGroup { client.systemInfo.getDockerVersionInfo() }

            it("succeeds") {
                assertThat(versionInfoRetrievalResult, isA<DockerVersionInfoRetrievalResult.Succeeded>())
            }
        }
    }
})

private fun createClient(posix: POSIX, nativeMethods: NativeMethods): DockerClient {
    val logger = mock<Logger>()
    val processRunner = ProcessRunner(logger)
    val systemInfo = SystemInfo(nativeMethods, FileSystems.getDefault())
    val dockerHost = getDockerHost(systemInfo)
    val tlsConfig = getDockerTLSConfig()
    val httpConfig = DockerHttpConfig(OkHttpClient(), dockerHost, tlsConfig, systemInfo)
    val containersAPI = ContainersAPI(httpConfig, systemInfo, logger)
    val execAPI = ExecAPI(httpConfig, systemInfo, logger)
    val imagesAPI = ImagesAPI(httpConfig, systemInfo, logger)
    val networksAPI = NetworksAPI(httpConfig, systemInfo, logger)
    val systemInfoAPI = SystemInfoAPI(httpConfig, systemInfo, logger)
    val consoleInfo = ConsoleInfo(nativeMethods, systemInfo, logger)
    val consoleManager = getConsoleManagerForPlatform(consoleInfo, processRunner, nativeMethods, logger)
    val credentialsConfigurationFile = DockerRegistryCredentialsConfigurationFile(FileSystems.getDefault(), processRunner, logger)
    val credentialsProvider = DockerRegistryCredentialsProvider(DockerRegistryDomainResolver(), DockerRegistryIndexResolver(), credentialsConfigurationFile)
    val ignoreParser = DockerIgnoreParser()
    val imageBuildContextFactory = DockerImageBuildContextFactory(ignoreParser)
    val dockerfileParser = DockerfileParser()
    val waiter = ContainerWaiter(containersAPI)
    val streamer = ContainerIOStreamer()
    val signalListener = SignalListener(posix)
    val consoleDimensions = ConsoleDimensions(nativeMethods, signalListener, logger)
    val ttyManager = ContainerTTYManager(containersAPI, consoleInfo, consoleDimensions, logger)

    val containersClient = DockerContainersClient(containersAPI, consoleManager, waiter, streamer, ttyManager, logger)
    val execClient = DockerExecClient(execAPI, streamer, logger)
    val imagesClient = DockerImagesClient(imagesAPI, credentialsProvider, imageBuildContextFactory, dockerfileParser, logger)
    val networksClient = DockerNetworksClient(networksAPI)
    val systemInfoClient = DockerSystemInfoClient(systemInfoAPI, logger)

    return DockerClient(containersClient, execClient, imagesClient, networksClient, systemInfoClient)
}

private fun getNativeMethodsForPlatform(posix: POSIX): NativeMethods = when (Platform.getNativePlatform().os) {
    Platform.OS.WINDOWS -> WindowsNativeMethods(posix)
    else -> UnixNativeMethods(posix)
}

private fun getConsoleManagerForPlatform(consoleInfo: ConsoleInfo, processRunner: ProcessRunner, nativeMethods: NativeMethods, logger: Logger): ConsoleManager =
    when (Platform.getNativePlatform().os) {
        Platform.OS.WINDOWS -> WindowsConsoleManager(consoleInfo, nativeMethods as WindowsNativeMethods, logger)
        else -> UnixConsoleManager(consoleInfo, processRunner, logger)
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

private fun removeImage(imageName: String) {
    val processRunner = ProcessRunner(mock())
    val result = processRunner.runAndCaptureOutput(listOf("docker", "rmi", "-f", imageName))

    assertThat(result, has(ProcessOutput::output, containsSubstring("No such image: $imageName")) or has(ProcessOutput::exitCode, equalTo(0)))
}

private fun getDockerHost(systemInfo: SystemInfo): String =
    System.getenv().getOrDefault("DOCKER_HOST", DockerHttpConfigDefaults(systemInfo).defaultDockerHost)

private fun getDockerTLSConfig(): DockerTLSConfig {
    if (System.getenv().getOrDefault("DOCKER_TLS_VERIFY", "0") != "1") {
        return DockerTLSConfig.DisableTLS
    }

    val certsDir = Paths.get(System.getenv().getValue("DOCKER_CERT_PATH"))

    return DockerTLSConfig.EnableTLS(
        true,
        certsDir.resolve("ca.pem"),
        certsDir.resolve("cert.pem"),
        certsDir.resolve("key.pem")
    )
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
