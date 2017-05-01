package decompose.docker

import decompose.config.Container

class DockerClient {
    fun build(container: Container): DockerImage = throw NotImplementedError()
    fun create(container: Container, image: DockerImage): DockerContainer = throw NotImplementedError()
    fun run(container: DockerContainer) = NotImplementedError()
}

data class DockerImage(val id: String)
data class DockerContainer(val id: String)
