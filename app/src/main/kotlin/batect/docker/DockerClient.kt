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

import batect.config.BuildImage
import batect.config.Container
import batect.logging.Logger
import batect.os.ExecutableDoesNotExistException
import batect.os.Exited
import batect.os.KillProcess
import batect.os.KilledDuringProcessing
import batect.os.ProcessOutput
import batect.os.ProcessRunner
import batect.ui.ConsoleInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JSON
import java.util.UUID

class DockerClient(
    private val imageLabellingStrategy: DockerImageLabellingStrategy,
    private val processRunner: ProcessRunner,
    private val creationCommandGenerator: DockerContainerCreationCommandGenerator,
    private val consoleInfo: ConsoleInfo,
    private val logger: Logger
) {
    private val buildImageIdLineRegex = """^Successfully built (.*)$""".toRegex(RegexOption.MULTILINE)

    fun build(projectName: String, container: Container, buildArgs: Map<String, String>, onStatusUpdate: (DockerImageBuildProgress) -> Unit): DockerImage {
        logger.info {
            message("Building image.")
            data("container", container)
            data("buildArgs", buildArgs)
        }

        val label = imageLabellingStrategy.labelImage(projectName, container)
        val command = listOf("docker", "build") +
            buildArgs.flatMap { (name, value) -> listOf("--build-arg", "$name=$value") } +
            listOf("--tag", label, (container.imageSource as BuildImage).buildDirectory)

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

        val imageId = buildImageIdLineRegex.findAll(result.output).lastOrNull()?.groupValues?.get(1) ?: label

        logger.info {
            message("Image build succeeded.")
            data("imageId", imageId)
        }

        return DockerImage(imageId)
    }

    fun create(request: DockerContainerCreationRequest): DockerContainer {
        logger.info {
            message("Creating container.")
            data("request", request)
        }

        val args = creationCommandGenerator.createCommandLine(request)
        val result = processRunner.runAndCaptureOutput(args)

        if (failed(result)) {
            logger.error {
                message("Container creation failed.")
                data("result", result)
            }

            throw ContainerCreationFailedException("Output from Docker was: ${result.output.trim()}")
        } else {
            val containerId = result.output.trim()

            logger.info {
                message("Container created.")
                data("containerId", containerId)
            }

            return DockerContainer(containerId)
        }
    }

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

    fun start(container: DockerContainer) {
        logger.info {
            message("Starting container.")
            data("container", container)
        }

        val command = listOf("docker", "start", container.id)
        val result = processRunner.runAndCaptureOutput(command)

        if (failed(result)) {
            logger.error {
                message("Starting container failed.")
                data("result", result)
            }

            throw ContainerStartFailedException(container.id, result.output)
        }

        logger.info {
            message("Container started.")
            data("container", container)
        }
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

            throw ContainerHealthCheckException("Checking if container '${container.id}' has a healthcheck failed. Output from Docker was: ${result.output.trim()}")
        }

        return result.output.trim() != "null"
    }

    fun getLastHealthCheckResult(container: DockerContainer): DockerHealthCheckResult {
        val command = listOf("docker", "inspect", container.id, "--format={{json .State.Health}}")
        val result = processRunner.runAndCaptureOutput(command)

        if (failed(result)) {
            logger.error {
                message("Could not get last health check result for container.")
                data("container", container)
                data("result", result)
            }

            throw ContainerHealthCheckException("Could not get the last health check result for container '${container.id}'. Output from Docker was: ${result.output.trim()}")
        }

        if (result.output.trim() == "null") {
            throw ContainerHealthCheckException("Could not get the last health check result for container '${container.id}'. The container does not have a health check.")
        }

        val parsed = JSON.nonstrict.parse<DockerHealthCheckStatus>(result.output)

        return parsed.log.last()
    }

    fun stop(container: DockerContainer) {
        logger.info {
            message("Stopping container.")
            data("container", container)
        }

        val command = listOf("docker", "stop", container.id)
        val result = processRunner.runAndCaptureOutput(command)

        if (failed(result)) {
            logger.error {
                message("Could not stop container.")
                data("result", result)
            }

            throw ContainerStopFailedException(container.id, result.output.trim())
        }

        logger.info {
            message("Container stopped.")
        }
    }

    fun createNewBridgeNetwork(): DockerNetwork {
        logger.info {
            message("Creating new network.")
        }

        val command = listOf("docker", "network", "create", "--driver", "bridge", UUID.randomUUID().toString())
        val result = processRunner.runAndCaptureOutput(command)

        if (failed(result)) {
            logger.error {
                message("Could not create network.")
                data("result", result)
            }

            throw NetworkCreationFailedException(result.output.trim())
        }

        val networkId = result.output.trim()

        logger.info {
            message("Network created.")
            data("networkId", networkId)
        }

        return DockerNetwork(networkId)
    }

    fun deleteNetwork(network: DockerNetwork) {
        logger.info {
            message("Deleting network.")
            data("network", network)
        }

        val command = listOf("docker", "network", "rm", network.id)
        val result = processRunner.runAndCaptureOutput(command)

        if (failed(result)) {
            logger.error {
                message("Could not delete network.")
                data("result", result)
            }

            throw NetworkDeletionFailedException(network.id, result.output.trim())
        }

        logger.info {
            message("Network deleted.")
        }
    }

    fun remove(container: DockerContainer) {
        logger.info {
            message("Removing container.")
            data("container", container)
        }

        val command = listOf("docker", "rm", container.id)
        val result = processRunner.runAndCaptureOutput(command)

        if (failed(result)) {
            logger.error {
                message("Could not remove container.")
                data("result", result)
            }

            if (result.output.startsWith("Error response from daemon: No such container: ")) {
                throw ContainerDoesNotExistException("Removing container '${container.id}' failed because it does not exist.")
            } else {
                throw ContainerRemovalFailedException(container.id, result.output.trim())
            }
        }

        logger.info {
            message("Container removed.")
        }
    }

    fun forciblyRemove(container: DockerContainer) {
        logger.info {
            message("Forcibly removing container.")
            data("container", container)
        }

        val command = listOf("docker", "rm", "--force", container.id)
        val result = processRunner.runAndCaptureOutput(command)

        if (failed(result)) {
            logger.error {
                message("Could not forcibly remove container.")
                data("result", result)
            }

            if (result.output.startsWith("Error response from daemon: No such container: ")) {
                throw ContainerDoesNotExistException("Removing container '${container.id}' failed because it does not exist.")
            } else {
                throw ContainerRemovalFailedException(container.id, result.output.trim())
            }
        }

        logger.info {
            message("Container forcibly removed.")
        }
    }

    fun getDockerVersionInfo(): String {
        val command = listOf("docker", "version", "--format", "Client: {{.Client.Version}} (API: {{.Client.APIVersion}}, commit: {{.Client.GitCommit}}), Server: {{.Server.Version}} (API: {{.Server.APIVersion}}, minimum supported API: {{.Server.MinAPIVersion}}, commit: {{.Server.GitCommit}})")

        try {
            val result = processRunner.runAndCaptureOutput(command)

            if (failed(result)) {
                logger.error {
                    message("Could not get Docker version info.")
                    data("result", result)
                }

                return "(Could not get Docker version information: ${result.output.trim()})"
            }

            return result.output.trim()
        } catch (t: Throwable) {
            logger.error {
                message("An exception was thrown while getting Docker version info.")
                exception(t)
            }

            return "(Could not get Docker version information because ${t.javaClass.simpleName} was thrown: ${t.message})"
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

            throw ImagePullFailedException("Pulling image '$imageName' failed. Output from Docker was: ${result.output.trim()}")
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

            throw ImagePullFailedException("Checking if image '$imageName' has already been pulled failed. Output from Docker was: ${result.output.trim()}")
        }

        val haveImage = result.output.trim().isNotEmpty()

        logger.info {
            message("Checking if image exists locally succeeded.")
            data("haveImage", haveImage)
        }

        return haveImage
    }

    private fun failed(result: ProcessOutput): Boolean = result.exitCode != 0

    @Serializable
    data class DockerHealthCheckStatus(@SerialName("Log") val log: List<DockerHealthCheckResult>)
}

data class DockerImage(val id: String)
data class DockerContainer(val id: String)
data class DockerContainerRunResult(val exitCode: Int)
data class DockerNetwork(val id: String)

@Serializable
data class DockerHealthCheckResult(@SerialName("ExitCode") val exitCode: Int, @SerialName("Output") val output: String)

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

class ImageBuildFailedException(val outputFromDocker: String) : RuntimeException("Image build failed. Output from Docker was: $outputFromDocker")

class ContainerCreationFailedException(message: String?, cause: Throwable?) : RuntimeException(message, cause) {
    constructor(message: String?) : this(message, null)
}

class ContainerStartFailedException(val containerId: String, val outputFromDocker: String) : RuntimeException("Starting container '$containerId' failed. Output from Docker was: $outputFromDocker")
class ContainerStopFailedException(val containerId: String, val outputFromDocker: String) : RuntimeException("Stopping container '$containerId' failed. Output from Docker was: $outputFromDocker")
class ImagePullFailedException(message: String) : RuntimeException(message)
class ContainerDoesNotExistException(message: String) : RuntimeException(message)
class ContainerHealthCheckException(message: String) : RuntimeException(message)
class NetworkCreationFailedException(val outputFromDocker: String) : RuntimeException("Creation of network failed. Output from Docker was: $outputFromDocker")
class ContainerRemovalFailedException(val containerId: String, val outputFromDocker: String) : RuntimeException("Removal of container '$containerId' failed. Output from Docker was: $outputFromDocker")
class NetworkDeletionFailedException(val networkId: String, val outputFromDocker: String) : RuntimeException("Deletion of network '$networkId' failed. Output from Docker was: $outputFromDocker")

enum class HealthStatus {
    NoHealthCheck,
    BecameHealthy,
    BecameUnhealthy,
    Exited
}
