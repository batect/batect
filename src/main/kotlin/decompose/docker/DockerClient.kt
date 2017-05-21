package decompose.docker

import decompose.config.Container

class DockerClient(
        private val imageLabellingStrategy: DockerImageLabellingStrategy,
        private val processRunner: ProcessRunner,
        private val creationCommandGenerator: DockerContainerCreationCommandGenerator) {

    fun build(projectName: String, container: Container): DockerImage {
        val label = imageLabellingStrategy.labelImage(projectName, container)
        val command = listOf("docker", "build", "--tag", label, container.buildDirectory)

        if (failed(processRunner.run(command))) {
            throw ImageBuildFailedException()
        }

        return DockerImage(label)
    }

    fun create(container: Container, command: String?, image: DockerImage): DockerContainer {
        val args = creationCommandGenerator.createCommandLine(container, command, image)
        val result = processRunner.runAndCaptureOutput(args)

        if (failed(result.exitCode)) {
            throw ContainerCreationFailedException("Creation of container '${container.name}' failed. Output from Docker was: ${result.output.trim()}")
        } else {
            return DockerContainer(result.output.trim())
        }
    }

    fun run(container: DockerContainer): DockerContainerRunResult {
        val command = listOf("docker", "start", "--attach", "--interactive", container.id)
        val exitCode = processRunner.run(command)

        return DockerContainerRunResult(exitCode)
    }

    private fun failed(exitCode: Int): Boolean = exitCode != 0
}

data class DockerImage(val id: String)
data class DockerContainer(val id: String)
data class DockerContainerRunResult(val exitCode: Int)

class ImageBuildFailedException : RuntimeException("Image build failed.")
class ContainerCreationFailedException(message: String) : RuntimeException(message)
