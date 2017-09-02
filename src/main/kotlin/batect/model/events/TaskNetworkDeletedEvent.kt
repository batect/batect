package batect.model.events

import batect.model.steps.FinishTaskStep

// if aborting, do nothing, otherwise, finish task with exit code
object TaskNetworkDeletedEvent : TaskEvent() {
    override fun apply(context: TaskEventContext) {
        if (context.isAborting) {
            return
        }

        val exitEvent = context.getSinglePastEventOfType<RunningContainerExitedEvent>()!!
        context.queueStep(FinishTaskStep(exitEvent.exitCode))
    }

    override fun toString() = this::class.simpleName!!
}
