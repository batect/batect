package batect.model

import batect.model.steps.BeginTaskStep

class TaskStateMachineProvider() {
    fun createStateMachine(graph: DependencyGraph): TaskStateMachine {
        val stateMachine = TaskStateMachine(graph)
        stateMachine.queueStep(BeginTaskStep)

        return stateMachine
    }
}
