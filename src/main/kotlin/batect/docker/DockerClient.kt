/*
   Copyright 2017 Charles Korn.

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

import batect.config.Container
import batect.ui.ConsoleInfo
import java.util.UUID

class DockerClient(
        private val imageLabellingStrategy: DockerImageLabellingStrategy,
        private val processRunner: ProcessRunner,
        private val creationCommandGenerator: DockerContainerCreationCommandGenerator,
        private val consoleInfo: ConsoleInfo
) {

    fun build(projectName: String, container: Container): DockerImage {
        val label = imageLabellingStrategy.labelImage(projectName, container)
        val command = listOf("docker", "build", "--tag", label, container.buildDirectory)
        val result = processRunner.runAndCaptureOutput(command)

        if (failed(result.exitCode)) {
            throw ImageBuildFailedException(result.output.trim())
        }

        return DockerImage(label)
    }

    fun create(container: Container, command: String?, image: DockerImage, network: DockerNetwork): DockerContainer {
        val args = creationCommandGenerator.createCommandLine(container, command, image, network)
        val result = processRunner.runAndCaptureOutput(args)

        if (failed(result.exitCode)) {
            throw ContainerCreationFailedException("Output from Docker was: ${result.output.trim()}")
        } else {
            return DockerContainer(result.output.trim(), container.name)
        }
    }

    fun run(container: DockerContainer): DockerContainerRunResult {
        val command = if (consoleInfo.stdinIsTTY) {
            listOf("docker", "start", "--attach", "--interactive", container.id)
        } else {
            listOf("docker", "start", "--attach", container.id)
        }

        val exitCode = processRunner.run(command)

        return DockerContainerRunResult(exitCode)
    }

    fun start(container: DockerContainer) {
        val command = listOf("docker", "start", container.id)
        val result = processRunner.runAndCaptureOutput(command)

        if (failed(result.exitCode)) {
            throw ContainerStartFailedException(container.id, result.output)
        }
    }

    fun waitForHealthStatus(container: DockerContainer): HealthStatus {
        if (!hasHealthCheck(container)) {
            return HealthStatus.NoHealthCheck
        }

        val command = listOf("docker", "events", "--since=0",
                "--format", "{{.Status}}",
                "--filter", "container=${container.id}",
                "--filter", "event=die",
                "--filter", "event=health_status")

        val result = processRunner.runAndProcessOutput(command) { line ->
            when {
                line == "health_status: healthy" -> KillProcess(HealthStatus.BecameHealthy)
                line == "health_status: unhealthy" -> KillProcess(HealthStatus.BecameUnhealthy)
                line.startsWith("health_status") -> throw ContainerHealthCheckException("Unexpected health_status event: $line")
                line == "die" -> KillProcess(HealthStatus.Exited)
                else -> throw ContainerHealthCheckException("Unexpected event received: $line")
            }
        }

        return when (result) {
            is KilledDuringProcessing -> result.result
            is Exited -> throw ContainerHealthCheckException("Event stream for container '${container.name}' exited early with exit code ${result.exitCode}.")
        }
    }

    private fun hasHealthCheck(container: DockerContainer): Boolean {
        val command = listOf("docker", "inspect", "--format", "{{json .Config.Healthcheck}}", container.id)
        val result = processRunner.runAndCaptureOutput(command)

        if (failed(result.exitCode)) {
            throw ContainerHealthCheckException("Checking if container '${container.name}' has a healthcheck failed. Output from Docker was: ${result.output}")
        }

        return result.output.trim() != "null"
    }

    fun stop(container: DockerContainer) {
        val command = listOf("docker", "stop", container.id)
        val result = processRunner.runAndCaptureOutput(command)

        if (failed(result.exitCode)) {
            throw ContainerStopFailedException(container.id, result.output.trim())
        }
    }

    fun createNewBridgeNetwork(): DockerNetwork {
        val command = listOf("docker", "network", "create", "--driver", "bridge", UUID.randomUUID().toString())
        val result = processRunner.runAndCaptureOutput(command)

        if (failed(result.exitCode)) {
            throw NetworkCreationFailedException(result.output.trim())
        }

        return DockerNetwork(result.output.trim())
    }

    fun deleteNetwork(network: DockerNetwork) {
        val command = listOf("docker", "network", "rm", network.id)
        val result = processRunner.runAndCaptureOutput(command)

        if (failed(result.exitCode)) {
            throw NetworkDeletionFailedException(network.id, result.output.trim())
        }
    }

    fun remove(container: DockerContainer) {
        val command = listOf("docker", "rm", container.id)
        val result = processRunner.runAndCaptureOutput(command)

        if (failed(result.exitCode)) {
            if (result.output.startsWith("Error response from daemon: No such container: ")) {
                throw ContainerDoesNotExistException("Removing container '${container.id}' failed because it does not exist.")
            } else {
                throw ContainerRemovalFailedException(container.id, result.output.trim())
            }
        }
    }

    fun forciblyRemove(container: DockerContainer) {
        val command = listOf("docker", "rm", "--force", container.id)
        val result = processRunner.runAndCaptureOutput(command)

        if (failed(result.exitCode)) {
            if (result.output.startsWith("Error response from daemon: No such container: ")) {
                throw ContainerDoesNotExistException("Removing container '${container.id}' failed because it does not exist.")
            } else {
                throw ContainerRemovalFailedException(container.id, result.output.trim())
            }
        }
    }

    private fun failed(exitCode: Int): Boolean = exitCode != 0
}

data class DockerImage(val id: String)
data class DockerContainer(val id: String, val name: String)
data class DockerContainerRunResult(val exitCode: Int)
data class DockerNetwork(val id: String)

class ImageBuildFailedException(val outputFromDocker: String) : RuntimeException("Image build failed. Output from Docker was: $outputFromDocker")
class ContainerCreationFailedException(message: String) : RuntimeException(message)
class ContainerStartFailedException(val containerId: String, val outputFromDocker: String) : RuntimeException("Starting container '$containerId' failed. Output from Docker was: $outputFromDocker")
class ContainerStopFailedException(val containerId: String, val outputFromDocker: String) : RuntimeException("Stopping container '$containerId' failed. Output from Docker was: $outputFromDocker")
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
