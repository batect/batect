package decompose

import decompose.config.Configuration
import decompose.model.DependencyGraphProvider
import decompose.model.TaskStateMachine
import decompose.model.TaskStateMachineProvider
import decompose.model.events.TaskEvent
import decompose.model.events.TaskEventSink
import decompose.model.steps.FinishTaskStep
import decompose.model.steps.TaskStepRunner

data class TaskRunner(
        private val eventLogger: EventLogger,
        private val taskStepRunner: TaskStepRunner,
        private val graphProvider: DependencyGraphProvider,
        private val stateMachineProvider: TaskStateMachineProvider
) {
    fun run(config: Configuration, taskName: String): Int {
        val resolvedTask = config.tasks[taskName] ?: throw ExecutionException("The task '$taskName' does not exist.")

        val graph = graphProvider.createGraph(config, resolvedTask)
        val stateMachine = stateMachineProvider.createStateMachine(graph)
        val eventSink = createEventSink(stateMachine)

        while (true) {
            val step = stateMachine.popNextStep()

            if (step == null) {
                eventLogger.taskFailed(taskName)
                return 1
            }

            eventLogger.logBeforeStartingStep(step)
            taskStepRunner.run(step, eventSink)

            if (step is FinishTaskStep) {
                return step.exitCode
            }
        }
    }

    private fun createEventSink(stateMachine: TaskStateMachine) = object : TaskEventSink {
        override fun postEvent(event: TaskEvent) {
            eventLogger.postEvent(event)
            stateMachine.postEvent(event)
        }
    }
}
