package decompose.model.events

import decompose.model.steps.CleanUpContainerStep
import decompose.model.steps.RunContainerStep
import decompose.model.steps.StartContainerStep
import decompose.config.Container
import decompose.docker.DockerContainer

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
