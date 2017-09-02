package batect.model.events

import batect.model.steps.RemoveContainerStep
import batect.model.steps.StopContainerStep
import batect.config.Container

data class RunningContainerExitedEvent(val container: Container, val exitCode: Int) : TaskEvent() {
    override fun apply(context: TaskEventContext) {
        if (!context.isTaskContainer(container)) {
            throw IllegalArgumentException("The container '${container.name}' is not the task container.")
        }

        val dockerContainer = context.getPastEventsOfType<ContainerCreatedEvent>()
                .single { it.container == container }
                .dockerContainer

        context.queueStep(RemoveContainerStep(container, dockerContainer))

        context.dependenciesOf(container)
                .forEach { stopContainer(it, context) }
    }

    private fun stopContainer(container: Container, context: TaskEventContext) {
        val dockerContainer = context.getPastEventsOfType<ContainerCreatedEvent>()
                .single { it.container == container }
                .dockerContainer

        context.queueStep(StopContainerStep(container, dockerContainer))
    }

    override fun toString() = "${this::class.simpleName}(container: '${container.name}', exit code: $exitCode)"
}
