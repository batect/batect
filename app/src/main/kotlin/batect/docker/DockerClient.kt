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
import batect.docker.run.ContainerKiller
import batect.docker.run.ContainerTTYManager
import batect.docker.run.ContainerWaiter
import batect.logging.Logger
import batect.ui.ConsoleInfo
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.nio.file.Paths
import java.time.Duration

// Unix sockets implementation inspired by
// https://github.com/gesellix/okhttp/blob/master/samples/simple-client/src/main/java/okhttp3/sample/OkDocker.java and
// https://github.com/square/okhttp/blob/master/samples/unixdomainsockets/src/main/java/okhttp3/unixdomainsockets/UnixDomainSocketFactory.java
class DockerClient(
    private val api: DockerAPI,
    private val consoleInfo: ConsoleInfo,
    private val credentialsProvider: DockerRegistryCredentialsProvider,
    private val imageBuildContextFactory: DockerImageBuildContextFactory,
    private val dockerfileParser: DockerfileParser,
    private val waiter: ContainerWaiter,
    private val ioStreamer: ContainerIOStreamer,
    private val killer: ContainerKiller,
    private val ttyManager: ContainerTTYManager,
    private val logger: Logger,
    private val imagePullProgressReporterFactory: () -> DockerImagePullProgressReporter = ::DockerImagePullProgressReporter
) {
    fun build(
        buildDirectory: String,
        buildArgs: Map<String, String>,
        imageTags: Set<String>,
        onStatusUpdate: (DockerImageBuildProgress) -> Unit
    ): DockerImage {
        logger.info {
            message("Building image.")
            data("buildDirectory", buildDirectory)
            data("buildArgs", buildArgs)
            data("imageTags", imageTags)
        }

        try {
            val buildPath = Paths.get(buildDirectory)
            val context = imageBuildContextFactory.createFromDirectory(buildPath)
            val baseImageName = dockerfileParser.extractBaseImageName(buildPath.resolve("Dockerfile"))
            val credentials = credentialsProvider.getCredentials(baseImageName)
            val reporter = imagePullProgressReporterFactory()
            var lastStepProgressUpdate: DockerImageBuildProgress? = null

            val image = api.buildImage(context, buildArgs, imageTags, credentials) { line ->
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

    fun run(container: DockerContainer): DockerContainerRunResult {
        logger.info {
            message("Running container.")
            data("container", container)
            data("stdinIsTTY", consoleInfo.stdinIsTTY)
        }

        val exitCodeSource = waiter.startWaitingForContainerToExit(container)

        api.attachToContainerOutput(container).use { outputStream ->
            api.attachToContainerInput(container).use { inputStream ->
                api.startContainer(container)

                ttyManager.monitorForSizeChanges(container).use {
                    killer.killContainerOnSigint(container).use {
                        consoleInfo.enterRawMode().use {
                            ioStreamer.stream(outputStream, inputStream)
                        }
                    }
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

    fun waitForHealthStatus(container: DockerContainer): HealthStatus {
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
            val event = api.waitForNextEventForContainer(container, setOf("die", "health_status"), timeout)

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

    fun pullImage(imageName: String, onProgressUpdate: (DockerImagePullProgress) -> Unit): DockerImage {
        try {
            if (!api.hasImage(imageName)) {
                val credentials = credentialsProvider.getCredentials(imageName)
                val reporter = imagePullProgressReporterFactory()

                api.pullImage(imageName, credentials) { progress ->
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
                message("Connectivity check succeeded.")
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
