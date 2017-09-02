package batect.model

import batect.config.Configuration
import batect.config.Task

class DependencyGraphProvider {
    fun createGraph(config: Configuration, task: Task) = DependencyGraph(config, task)
}
