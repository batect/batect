package batect.model.events

import batect.model.steps.CleanUpContainerStep
import batect.model.steps.RunContainerStep
import batect.model.steps.StartContainerStep
import batect.config.Container
import batect.docker.DockerContainer

data class ContainerCreatedEvent(val container: Container, val dockerContainer: DockerContainer) : TaskEvent() {
    override fun apply(context: TaskEventContext) {
        if (context.isAborting) {
            context.queueStep(CleanUpContainerStep(container, dockerContainer))
            return
        }

        val dependencies = context.dependenciesOf(container)
        val healthyContainers = context.getPastEventsOfType<ContainerBecameHealthyEvent>()
                .mapTo(mutableSetOf()) { it.container }

        if (healthyContainers.containsAll(dependencies)) {
            if (context.isTaskContainer(container)) {
                context.queueStep(RunContainerStep(container, dockerContainer))
            } else {
                context.queueStep(StartContainerStep(container, dockerContainer))
            }
        }
    }

    override fun toString() = "${this::class.simpleName}(container: '${container.name}', Docker container ID: '${dockerContainer.id}')"
}
