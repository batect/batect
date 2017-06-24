package decompose

import decompose.config.Configuration
import decompose.config.Container
import decompose.config.Task

class DependencyResolver {
    fun resolveDependencies(config: Configuration, task: Task): Set<Container> {
        if (task.dependencies.contains(task.runConfiguration.container)) {
            throw DependencyResolutionFailedException("The task '${task.name}' cannot depend on the container '${task.runConfiguration.container}' and also run it.")
        }

        return task.dependencies.map { name ->
            resolveDependency(config, task, name)
        }.toSet()
    }

    private fun resolveDependency(config: Configuration, task: Task, name: String): Container {
        return config.containers[name] ?: throw DependencyResolutionFailedException("The container '$name' referenced by task '${task.name}' does not exist.")
    }
}
