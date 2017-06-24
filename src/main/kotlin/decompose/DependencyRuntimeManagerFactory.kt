package decompose

import decompose.config.Configuration
import decompose.config.Task
import decompose.docker.DockerClient

class DependencyRuntimeManagerFactory(private val dependencyResolver: DependencyResolver, private val eventLogger: EventLogger, private val dockerClient: DockerClient) {
    fun create(config: Configuration, task: Task): DependencyRuntimeManager {
        val dependencies = dependencyResolver.resolveDependencies(config, task)
        return DependencyRuntimeManager(config.projectName, dependencies, eventLogger, dockerClient)
    }
}
