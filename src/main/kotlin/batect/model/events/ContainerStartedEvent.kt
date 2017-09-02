package batect.model.events

import batect.model.steps.WaitForContainerToBecomeHealthyStep
import batect.config.Container

data class ContainerStartedEvent(val container: Container) : TaskEvent() {
    override fun apply(context: TaskEventContext) {
        if (context.isAborting) {
            return
        }

        val creationEvent = context.getPastEventsOfType<ContainerCreatedEvent>()
                .single { it.container == container }

        context.queueStep(WaitForContainerToBecomeHealthyStep(container, creationEvent.dockerContainer))
    }

    override fun toString() = "${this::class.simpleName}(container: '${container.name}')"
}
