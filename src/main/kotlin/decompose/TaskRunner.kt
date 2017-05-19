package decompose

import decompose.config.Configuration
import decompose.docker.DockerClient

data class TaskRunner(val dockerClient: DockerClient) {
    fun run(config: Configuration, task: String): Int {
        val resolvedTask = config.tasks[task] ?: throw ExecutionException("The task '$task' does not exist.")
        val runConfiguration = resolvedTask.runConfiguration
        val containerName = runConfiguration.container
        val resolvedContainer = config.containers[containerName] ?: throw ExecutionException("The container '$containerName' referenced by task '$task' does not exist.")

        if (resolvedTask.dependencies.isNotEmpty()) {
            throw ExecutionException("Running tasks with dependencies isn't supported yet.")
        }

        val image = dockerClient.build(config.projectName, resolvedContainer)
        val container = dockerClient.create(resolvedContainer, resolvedTask.runConfiguration.command, image)

        return dockerClient.run(container).exitCode
    }
}
