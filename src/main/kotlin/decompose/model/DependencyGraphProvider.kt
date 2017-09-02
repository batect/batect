package decompose.model

import decompose.config.Configuration
import decompose.config.Task

class DependencyGraphProvider {
    fun createGraph(config: Configuration, task: Task) = DependencyGraph(config, task)
}
