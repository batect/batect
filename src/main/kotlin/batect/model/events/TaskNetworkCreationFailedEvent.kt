package batect.model.events

data class TaskNetworkCreationFailedEvent(val message: String) : PreTaskRunFailureEvent() {
    override val messageToDisplay: String
        get() = "Could not create network for task: $message"

    override fun toString() = "${this::class.simpleName}(message: '$message')"
}
