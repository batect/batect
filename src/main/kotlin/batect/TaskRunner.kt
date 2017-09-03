package batect

import batect.config.Configuration
import batect.model.DependencyGraphProvider
import batect.model.TaskStateMachineProvider

data class TaskRunner(
        private val eventLogger: EventLogger,
        private val graphProvider: DependencyGraphProvider,
        private val stateMachineProvider: TaskStateMachineProvider,
        private val executionManagerProvider: ParallelExecutionManagerProvider
) {
    fun run(config: Configuration, taskName: String): Int {
        val resolvedTask = config.tasks[taskName]

        if (resolvedTask == null) {
            eventLogger.logTaskDoesNotExist(taskName)
            return -1
        }

        val graph = graphProvider.createGraph(config, resolvedTask)
        val stateMachine = stateMachineProvider.createStateMachine(graph)
        val executionManager = executionManagerProvider.createParallelExecutionManager(stateMachine, taskName)

        eventLogger.reset()

        return executionManager.run()
    }
}
