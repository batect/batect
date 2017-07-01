package decompose

import decompose.config.Container
import decompose.docker.DockerClient
import decompose.docker.DockerContainer
import decompose.docker.DockerImage
import decompose.docker.DockerNetwork
import decompose.docker.HealthStatus

// This class is not thread safe, do not call its methods from different threads at the same time.
data class DependencyRuntimeManager(val projectName: String, val dependencies: Set<Container>, val eventLogger: EventLogger, val dockerClient: DockerClient) {
    private var hasBuiltImages = false
    private var builtImages: Map<Container, DockerImage> = emptyMap()
    private val runningContainers: MutableSet<DockerContainer> = mutableSetOf()

    fun buildImages() {
        builtImages = dependencies.associate { dependency ->
            eventLogger.imageBuildStarting(dependency)

            val builtImage = dockerClient.build(projectName, dependency)
            Pair(dependency, builtImage)
        }

        hasBuiltImages = true
    }

    fun startDependencies(network: DockerNetwork) {
        if (!hasBuiltImages) {
            throw ExecutionException("Cannot start dependencies if their images have not yet been built. Call buildImages() before calling startDependencies().")
        }

        builtImages.forEach { container, image ->
            eventLogger.dependencyStarting(container)
            val dockerContainer = dockerClient.create(container, null, image, network)
            dockerClient.start(dockerContainer)
            runningContainers.add(dockerContainer)

            val healthStatus = dockerClient.waitForHealthStatus(dockerContainer)

            if (healthStatus == HealthStatus.Exited) {
                throw DependencyStartException("Dependency '${container.name}' exited unexpectedly.")
            } else if (healthStatus == HealthStatus.BecameUnhealthy) {
                throw DependencyStartException("Dependency '${container.name}' started but reported that it is not healthy.")
            }
        }
    }

    fun stopDependencies() {
        runningContainers.forEach { dockerClient.stop(it) }
        runningContainers.clear()
    }
}

class DependencyResolutionFailedException(message: String) : Exception(message)
class DependencyStartException(message: String) : Exception(message)
