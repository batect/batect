package decompose

import decompose.config.Configuration
import decompose.config.TaskRunConfiguration
import decompose.docker.DockerClient
import decompose.docker.DockerNetwork

data class TaskRunner(private val dockerClient: DockerClient, private val eventLogger: EventLogger, private val dependencyRuntimeManagerFactory: DependencyRuntimeManagerFactory) {
    fun run(config: Configuration, task: String): Int {
        val resolvedTask = config.tasks[task] ?: throw ExecutionException("The task '$task' does not exist.")
        val runConfiguration = resolvedTask.runConfiguration
        val resolvedContainer = resolveContainer(config, runConfiguration, task)
        val dependencyManager = dependencyRuntimeManagerFactory.create(config, resolvedTask)

        eventLogger.imageBuildStarting(resolvedContainer)
        val image = dockerClient.build(config.projectName, resolvedContainer)

        dependencyManager.buildImages()

        val network = dockerClient.createNewBridgeNetwork()

        try {
            dependencyManager.startDependencies(network)

            val command = resolvedTask.runConfiguration.command
            val container = dockerClient.create(resolvedContainer, command, image, network)

            eventLogger.commandStarting(resolvedContainer, command)
            return dockerClient.run(container).exitCode
        } finally {
            dependencyManager.stopDependencies()
            dockerClient.deleteNetwork(network)
        }
    }

    private fun resolveContainer(config: Configuration, runConfiguration: TaskRunConfiguration, task: String) =
            config.containers[runConfiguration.container] ?: throw ExecutionException("The container '${runConfiguration.container}' referenced by task '$task' does not exist.")
}
