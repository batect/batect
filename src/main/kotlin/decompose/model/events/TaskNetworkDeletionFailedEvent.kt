package decompose.model.events

import decompose.DisplayTaskFailureStep

data class TaskNetworkDeletionFailedEvent(val message: String) : TaskEvent() {
    override fun apply(context: TaskEventContext) {
        val network = context.getSinglePastEventOfType<TaskNetworkCreatedEvent>()!!.network

        val message = "${situationDescription(context)}, the network '${network.id}' could not be deleted: $message\n\n" +
                "This network may not have been removed, so you may need to clean up this network yourself by running 'docker network rm ${network.id}'.\n"

        context.queueStep(DisplayTaskFailureStep(message))

        if (!context.isAborting) {
            context.abort()
        }
    }

    private fun situationDescription(context: TaskEventContext): String {
        if (context.isAborting) {
            return "During clean up after the previous failure"
        } else {
            val exitCode = context.getSinglePastEventOfType<RunningContainerExitedEvent>()!!.exitCode

            return "After the task exited with exit code $exitCode"
        }
    }

    override fun toString() = "${this::class.simpleName}(message: '$message')"
}
