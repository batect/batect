package batect

import batect.config.Configuration
import batect.model.DependencyGraphProvider
import batect.model.TaskStateMachine
import batect.model.TaskStateMachineProvider
import batect.model.events.TaskEvent
import batect.model.events.TaskEventSink
import batect.model.steps.FinishTaskStep
import batect.model.steps.TaskStepRunner

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

        eventLogger.reset()

        while (true) {
            val step = stateMachine.popNextStep()

            if (step == null) {
                eventLogger.logTaskFailed(taskName)
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
