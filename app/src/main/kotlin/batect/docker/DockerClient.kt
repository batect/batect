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

package batect.docker

import batect.logging.Logger
import batect.os.ExecutableDoesNotExistException
import batect.os.Exited
import batect.os.KillProcess
import batect.os.KilledDuringProcessing
import batect.os.ProcessOutput
import batect.os.ProcessRunner
import batect.ui.ConsoleInfo

// Unix sockets implementation inspired by
// https://github.com/gesellix/okhttp/blob/master/samples/simple-client/src/main/java/okhttp3/sample/OkDocker.java and
// https://github.com/square/okhttp/blob/master/samples/unixdomainsockets/src/main/java/okhttp3/unixdomainsockets/UnixDomainSocketFactory.java
class DockerClient(
    private val processRunner: ProcessRunner,
    private val api: DockerAPI,
    private val consoleInfo: ConsoleInfo,
    private val logger: Logger
) {
    private val buildImageIdLineRegex = """^Successfully built (.*)$""".toRegex(RegexOption.MULTILINE)

    fun build(buildDirectory: String, buildArgs: Map<String, String>, onStatusUpdate: (DockerImageBuildProgress) -> Unit): DockerImage {
        logger.info {
            message("Building image.")
            data("buildDirectory", buildDirectory)
            data("buildArgs", buildArgs)
        }

        val buildArgsFlags = buildArgs.flatMap { (name, value) -> listOf("--build-arg", "$name=$value") }

        val command = listOf("docker", "build") +
            buildArgsFlags +
            buildDirectory

        val result = processRunner.runAndStreamOutput(command) { line ->
            logger.debug {
                message("Received output from Docker during image build.")
                data("outputLine", line)
            }

            val progress = DockerImageBuildProgress.fromBuildOutput(line)

            if (progress != null) {
                onStatusUpdate(progress)
            }
        }

        if (failed(result)) {
            logger.error {
                message("Image build failed.")
                data("result", result)
            }

            throw ImageBuildFailedException(result.output.trim())
        }

        val imageId = buildImageIdLineRegex.findAll(result.output).last().groupValues.get(1)

        logger.info {
            message("Image build succeeded.")
            data("imageId", imageId)
        }

        return DockerImage(imageId)
    }

    fun create(creationRequest: DockerContainerCreationRequest): DockerContainer = api.createContainer(creationRequest)
    fun start(container: DockerContainer) = api.startContainer(container)
    fun stop(container: DockerContainer) = api.stopContainer(container)
    fun remove(container: DockerContainer) = api.removeContainer(container)

    fun run(container: DockerContainer): DockerContainerRunResult {
        val command = if (consoleInfo.stdinIsTTY) {
            listOf("docker", "start", "--attach", "--interactive", container.id)
        } else {
            listOf("docker", "start", "--attach", container.id)
        }

        logger.info {
            message("Running container.")
            data("container", container)
            data("interactiveMode", consoleInfo.stdinIsTTY)
        }

        val exitCode = processRunner.run(command)

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

        if (!hasHealthCheck(container)) {
            logger.warn {
                message("Container has no health check.")
            }

            return HealthStatus.NoHealthCheck
        }

        val command = listOf("docker", "events", "--since=0",
            "--format", "{{.Status}}",
            "--filter", "container=${container.id}",
            "--filter", "event=die",
            "--filter", "event=health_status")

        val result = processRunner.runAndProcessOutput(command) { line ->
            logger.debug {
                message("Received event notification from Docker.")
                data("event", line)
            }

            when {
                line == "health_status: healthy" -> KillProcess(HealthStatus.BecameHealthy)
                line == "health_status: unhealthy" -> KillProcess(HealthStatus.BecameUnhealthy)
                line.startsWith("health_status") -> throw ContainerHealthCheckException("Unexpected health_status event: $line")
                line == "die" -> KillProcess(HealthStatus.Exited)
                else -> throw ContainerHealthCheckException("Unexpected event received: $line")
            }
        }

        when (result) {
            is KilledDuringProcessing -> {
                val healthStatus = result.result

                logger.info {
                    message("Received health status for container.")
                    data("status", healthStatus)
                }

                return healthStatus
            }
            is Exited -> {
                logger.error {
                    message("Event stream for container exited early.")
                    data("result", result)
                }

                throw ContainerHealthCheckException("Event stream for container '${container.id}' exited early with exit code ${result.exitCode}.")
            }
        }
    }

    private fun hasHealthCheck(container: DockerContainer): Boolean {
        val command = listOf("docker", "inspect", "--format", "{{json .Config.Healthcheck}}", container.id)
        val result = processRunner.runAndCaptureOutput(command)

        if (failed(result)) {
            logger.error {
                message("Could not determine if container has a health check or not.")
                data("result", result)
            }

            throw ContainerHealthCheckException("Checking if container '${container.id}' has a healthcheck failed: ${result.output.trim()}")
        }

        return result.output.trim() != "{}"
    }

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

    fun pullImage(imageName: String): DockerImage {
        if (haveImageLocally(imageName)) {
            return DockerImage(imageName)
        }

        logger.info {
            message("Pulling image.")
            data("imageName", imageName)
        }

        val command = listOf("docker", "pull", imageName)
        val result = processRunner.runAndCaptureOutput(command)

        if (failed(result)) {
            logger.error {
                message("Could not pull image.")
                data("result", result)
            }

            throw ImagePullFailedException("Pulling image '$imageName' failed: ${result.output.trim()}")
        }

        logger.info {
            message("Image pulled.")
        }

        return DockerImage(imageName)
    }

    fun checkIfDockerIsAvailable(): Boolean {
        logger.info {
            message("Checking if Docker is available.")
        }

        try {
            val result = processRunner.runAndCaptureOutput(listOf("docker", "--version"))

            if (failed(result)) {
                logger.error {
                    message("'docker --version' returned unexpected exit code.")
                    data("result", result)
                }

                return false
            }

            logger.info {
                message("Docker is available.")
                data("result", result)
            }

            return true
        } catch (e: ExecutableDoesNotExistException) {
            logger.warn {
                message("The Docker executable is not available.")
                exception(e)
            }

            return false
        }
    }

    private fun haveImageLocally(imageName: String): Boolean {
        logger.info {
            message("Checking if image exists locally.")
            data("imageName", imageName)
        }

        val command = listOf("docker", "images", "-q", imageName)
        val result = processRunner.runAndCaptureOutput(command)

        if (failed(result)) {
            logger.error {
                message("Could not check if image exists locally.")
                data("result", result)
            }

            throw ImagePullFailedException("Checking if image '$imageName' has already been pulled failed: ${result.output.trim()}")
        }

        val haveImage = result.output.trim().isNotEmpty()

        logger.info {
            message("Checking if image exists locally succeeded.")
            data("haveImage", haveImage)
        }

        return haveImage
    }

    private fun failed(result: ProcessOutput): Boolean = result.exitCode != 0
}

data class DockerImageBuildProgress(val currentStep: Int, val totalSteps: Int, val message: String) {
    companion object {
        private val buildStepLineRegex = """^Step (\d+)/(\d+) : (.*)$""".toRegex()

        fun fromBuildOutput(line: String): DockerImageBuildProgress? {
            val stepLineMatch = buildStepLineRegex.matchEntire(line)

            if (stepLineMatch == null) {
                return null
            }

            return DockerImageBuildProgress(stepLineMatch.groupValues[1].toInt(), stepLineMatch.groupValues[2].toInt(), stepLineMatch.groupValues[3])
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

enum class HealthStatus {
    NoHealthCheck,
    BecameHealthy,
    BecameUnhealthy,
    Exited
}
