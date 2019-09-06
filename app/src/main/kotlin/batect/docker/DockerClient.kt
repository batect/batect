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

package batect.docker

import batect.docker.build.DockerImageBuildContextFactory
import batect.docker.build.DockerfileParser
import batect.docker.pull.DockerImagePullProgress
import batect.docker.pull.DockerImagePullProgressReporter
import batect.docker.pull.DockerRegistryCredentialsProvider
import batect.docker.run.ContainerIOStreamer
import batect.docker.run.ContainerTTYManager
import batect.docker.run.ContainerWaiter
import batect.docker.run.InputConnection
import batect.docker.run.OutputConnection
import batect.execution.CancellationContext
import batect.logging.Logger
import batect.os.ConsoleManager
import batect.utils.Version
import batect.utils.VersionComparisonMode
import kotlinx.serialization.json.JsonObject
import okio.Sink
import okio.Source
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Duration

// Unix sockets implementation inspired by
// https://github.com/gesellix/okhttp/blob/master/samples/simple-client/src/main/java/okhttp3/sample/OkDocker.java and
// https://github.com/square/okhttp/blob/master/samples/unixdomainsockets/src/main/java/okhttp3/unixdomainsockets/UnixDomainSocketFactory.java
class DockerClient(
    private val api: DockerAPI,
    private val consoleManager: ConsoleManager,
    private val credentialsProvider: DockerRegistryCredentialsProvider,
    private val imageBuildContextFactory: DockerImageBuildContextFactory,
    private val dockerfileParser: DockerfileParser,
    private val waiter: ContainerWaiter,
    private val ioStreamer: ContainerIOStreamer,
    private val ttyManager: ContainerTTYManager,
    private val logger: Logger,
    private val imagePullProgressReporterFactory: () -> DockerImagePullProgressReporter = ::DockerImagePullProgressReporter
) {
    fun build(
        buildDirectory: Path,
        buildArgs: Map<String, String>,
        dockerfilePath: String,
        imageTags: Set<String>,
        cancellationContext: CancellationContext,
        onStatusUpdate: (DockerImageBuildProgress) -> Unit
    ): DockerImage {
        logger.info {
            message("Building image.")
            data("buildDirectory", buildDirectory)
            data("buildArgs", buildArgs)
            data("dockerfilePath", dockerfilePath)
            data("imageTags", imageTags)
        }

        try {
            val resolvedDockerfilePath = buildDirectory.resolve(dockerfilePath)

            if (!Files.exists(resolvedDockerfilePath)) {
                throw ImageBuildFailedException("Could not build image: the Dockerfile '$dockerfilePath' does not exist in '$buildDirectory'")
            }

            if (!resolvedDockerfilePath.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(buildDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS))) {
                throw ImageBuildFailedException("Could not build image: the Dockerfile '$dockerfilePath' is not a child of '$buildDirectory'")
            }

            val context = imageBuildContextFactory.createFromDirectory(buildDirectory, dockerfilePath)
            val baseImageName = dockerfileParser.extractBaseImageName(resolvedDockerfilePath)
            val credentials = credentialsProvider.getCredentials(baseImageName)
            val reporter = imagePullProgressReporterFactory()
            var lastStepProgressUpdate: DockerImageBuildProgress? = null

            val image = api.buildImage(context, buildArgs, dockerfilePath, imageTags, credentials, cancellationContext) { line ->
                logger.debug {
                    message("Received output from Docker during image build.")
                    data("outputLine", line.toString())
                }

                val stepProgress = DockerImageBuildProgress.fromBuildOutput(line)

                if (stepProgress != null) {
                    lastStepProgressUpdate = stepProgress
                    onStatusUpdate(lastStepProgressUpdate!!)
                }

                val pullProgress = reporter.processProgressUpdate(line)

                if (pullProgress != null && lastStepProgressUpdate != null) {
                    lastStepProgressUpdate = lastStepProgressUpdate!!.copy(pullProgress = pullProgress)
                    onStatusUpdate(lastStepProgressUpdate!!)
                }
            }

            logger.info {
                message("Image build succeeded.")
                data("image", image)
            }

            return image
        } catch (e: DockerRegistryCredentialsException) {
            throw ImageBuildFailedException("Could not build image: ${e.message}", e)
        }
    }

    fun create(creationRequest: DockerContainerCreationRequest): DockerContainer = api.createContainer(creationRequest)
    fun start(container: DockerContainer) = api.startContainer(container)
    fun stop(container: DockerContainer) = api.stopContainer(container)
    fun remove(container: DockerContainer) = api.removeContainer(container)

    fun run(container: DockerContainer, stdout: Sink?, stdin: Source?): DockerContainerRunResult {
        logger.info {
            message("Running container.")
            data("container", container)
        }

        if (stdout == null && stdin != null) {
            throw DockerException("Attempted to stream input to container without streaming container output.")
        }

        val exitCodeSource = waiter.startWaitingForContainerToExit(container)

        connectContainerOutput(container, stdout).use { outputConnection ->
            connectContainerInput(container, stdin).use { inputConnection ->
                api.startContainer(container)

                startTTYEmulationIfRequired(container, stdin) {
                    ioStreamer.stream(outputConnection, inputConnection)
                }
            }
        }

        val exitCode = exitCodeSource.get()

        logger.info {
            message("Container run finished.")
            data("exitCode", exitCode)
        }

        return DockerContainerRunResult(exitCode)
    }

    private fun connectContainerOutput(container: DockerContainer, stdout: Sink?): OutputConnection {
        if (stdout == null) {
            return OutputConnection.Disconnected
        }

        return OutputConnection.Connected(api.attachToContainerOutput(container), stdout)
    }

    private fun connectContainerInput(container: DockerContainer, stdin: Source?): InputConnection {
        if (stdin == null) {
            return InputConnection.Disconnected
        }

        return InputConnection.Connected(stdin, api.attachToContainerInput(container))
    }

    private fun startTTYEmulationIfRequired(container: DockerContainer, stdin: Source?, action: () -> Unit) {
        if (stdin == null) {
            action()
            return
        }

        ttyManager.monitorForSizeChanges(container).use {
            consoleManager.enterRawMode().use {
                action()
            }
        }
    }

    fun waitForHealthStatus(container: DockerContainer, cancellationContext: CancellationContext): HealthStatus {
        logger.info {
            message("Checking health status of container.")
            data("container", container)
        }

        try {
            val info = api.inspectContainer(container)

            if (!hasHealthCheck(info)) {
                logger.warn {
                    message("Container has no health check.")
                }

                return HealthStatus.NoHealthCheck
            }

            val healthCheckInfo = info.config.healthCheck
            val checkPeriod = (healthCheckInfo.interval + healthCheckInfo.timeout).multipliedBy(healthCheckInfo.retries.toLong())
            val overheadMargin = Duration.ofSeconds(1)
            val timeout = healthCheckInfo.startPeriod + checkPeriod + overheadMargin
            val event = api.waitForNextEventForContainer(container, setOf("die", "health_status"), timeout, cancellationContext)

            logger.info {
                message("Received event notification from Docker.")
                data("event", event)
            }

            when {
                event.status == "health_status: healthy" -> return HealthStatus.BecameHealthy
                event.status == "health_status: unhealthy" -> return HealthStatus.BecameUnhealthy
                event.status == "die" -> return HealthStatus.Exited
                else -> throw ContainerHealthCheckException("Unexpected event received: ${event.status}")
            }
        } catch (e: ContainerInspectionFailedException) {
            throw ContainerHealthCheckException("Checking if container '${container.id}' has a health check failed: ${e.message}", e)
        } catch (e: DockerException) {
            throw ContainerHealthCheckException("Waiting for health status of container '${container.id}' failed: ${e.message}", e)
        }
    }

    private fun hasHealthCheck(info: DockerContainerInfo): Boolean = info.config.healthCheck.test != null

    fun getLastHealthCheckResult(container: DockerContainer): DockerHealthCheckResult {
        try {
            val info = api.inspectContainer(container)

            if (info.state.health == null) {
                throw ContainerHealthCheckException("Could not get the last health check result for container '${container.id}'. The container does not have a health check.")
            }

            return info.state.health.log.last()
        } catch (e: ContainerInspectionFailedException) {
            throw ContainerHealthCheckException("Could not get the last health check result for container '${container.id}': ${e.message}", e)
        }
    }

    fun createNewBridgeNetwork(): DockerNetwork = api.createNetwork()
    fun deleteNetwork(network: DockerNetwork) = api.deleteNetwork(network)

    // Why does this method not just throw exceptions when things fail, like the other methods in this class do?
    // It's used in a number of places where throwing exceptions would be undesirable or unsafe (eg. during logging startup
    // and when showing version info), so instead we wrap the result.
    fun getDockerVersionInfo(): DockerVersionInfoRetrievalResult {
        try {
            val info = api.getServerVersionInfo()

            return DockerVersionInfoRetrievalResult.Succeeded(info)
        } catch (t: Throwable) {
            logger.error {
                message("An exception was thrown while getting Docker version info.")
                exception(t)
            }

            return DockerVersionInfoRetrievalResult.Failed("Could not get Docker version information because ${t.javaClass.simpleName} was thrown: ${t.message}")
        }
    }

    fun pullImage(imageName: String, cancellationContext: CancellationContext, onProgressUpdate: (DockerImagePullProgress) -> Unit): DockerImage {
        try {
            if (!api.hasImage(imageName)) {
                val credentials = credentialsProvider.getCredentials(imageName)
                val reporter = imagePullProgressReporterFactory()

                api.pullImage(imageName, credentials, cancellationContext) { progress ->
                    val progressUpdate = reporter.processProgressUpdate(progress)

                    if (progressUpdate != null) {
                        onProgressUpdate(progressUpdate)
                    }
                }
            }

            return DockerImage(imageName)
        } catch (e: DockerRegistryCredentialsException) {
            throw ImagePullFailedException("Could not pull image '$imageName': ${e.message}", e)
        }
    }

    fun checkConnectivity(): DockerConnectivityCheckResult {
        logger.info {
            message("Checking Docker daemon connectivity.")
        }

        try {
            api.ping()

            logger.info {
                message("Ping succeeded.")
            }

            val versionInfo = api.getServerVersionInfo()

            logger.info {
                message("Getting version info succeeded.")
                data("versionInfo", versionInfo)
            }

            if (Version.parse(versionInfo.apiVersion).compareTo(Version.parse(minimumDockerAPIVersion), VersionComparisonMode.DockerStyle) < 0) {
                return DockerConnectivityCheckResult.Failed("batect requires Docker $minimumDockerVersion or later, but version ${versionInfo.version} is installed.")
            }

            return DockerConnectivityCheckResult.Succeeded
        } catch (e: DockerException) {
            logger.warn {
                message("Connectivity check failed.")
                exception(e)
            }

            return DockerConnectivityCheckResult.Failed(e.message!!)
        } catch (e: IOException) {
            logger.warn {
                message("Connectivity check failed.")
                exception(e)
            }

            return DockerConnectivityCheckResult.Failed(e.message!!)
        }
    }
}

data class DockerImageBuildProgress(val currentStep: Int, val totalSteps: Int, val message: String, val pullProgress: DockerImagePullProgress?) {
    companion object {
        private val buildStepLineRegex = """^Step (\d+)/(\d+) : (.*)$""".toRegex()

        fun fromBuildOutput(line: JsonObject): DockerImageBuildProgress? {
            val output = line.getPrimitiveOrNull("stream")?.content

            if (output == null) {
                return null
            }

            val stepLineMatch = buildStepLineRegex.matchEntire(output)

            if (stepLineMatch == null) {
                return null
            }

            return DockerImageBuildProgress(stepLineMatch.groupValues[1].toInt(), stepLineMatch.groupValues[2].toInt(), stepLineMatch.groupValues[3], null)
        }
    }
}

sealed class DockerVersionInfoRetrievalResult {
    data class Succeeded(val info: DockerVersionInfo) : DockerVersionInfoRetrievalResult() {
        override fun toString(): String = info.toString()
    }

    data class Failed(val message: String) : DockerVersionInfoRetrievalResult() {
        override fun toString(): String = "($message)"
    }
}

sealed class DockerConnectivityCheckResult {
    object Succeeded : DockerConnectivityCheckResult()

    data class Failed(val message: String) : DockerConnectivityCheckResult()
}

enum class HealthStatus {
    NoHealthCheck,
    BecameHealthy,
    BecameUnhealthy,
    Exited
}
