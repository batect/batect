package decompose.docker

import decompose.config.Container

class DockerClient {
    fun build(container: Container): DockerImage = throw NotImplementedError()
    fun create(container: Container, image: DockerImage): DockerContainer = throw NotImplementedError()
    fun run(container: DockerContainer): DockerContainerRunResult = throw NotImplementedError()
}

data class DockerImage(val id: String)
data class DockerContainer(val id: String)
data class DockerContainerRunResult(val exitCode: Int)
