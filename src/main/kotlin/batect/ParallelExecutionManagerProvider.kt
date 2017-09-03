package batect

import batect.model.TaskStateMachine
import batect.model.steps.TaskStepRunner

class ParallelExecutionManagerProvider(private val eventLogger: EventLogger, private val taskStepRunner: TaskStepRunner) {
    private val maximumConcurrentSteps = 8 // TODO: make this configurable

    fun createParallelExecutionManager(stateMachine: TaskStateMachine, taskName: String) =
            ParallelExecutionManager(eventLogger, taskStepRunner, stateMachine, taskName, maximumConcurrentSteps)
}
