package batect.model.events

import batect.model.steps.RemoveContainerStep
import batect.model.steps.StopContainerStep
import batect.config.Container

data class ContainerStoppedEvent(val container: Container) : TaskEvent() {
    override fun apply(context: TaskEventContext) {
        if (context.isAborting) {
            return
        }

        val dockerContainer = context.getPastEventsOfType<ContainerCreatedEvent>()
                .single { it.container == container }
                .dockerContainer

        context.queueStep(RemoveContainerStep(container, dockerContainer))

        val stoppedContainers = context.getPastEventsOfType<ContainerStoppedEvent>()
                .mapTo(mutableSetOf()) { it.container }

        context.dependenciesOf(container)
                .filter { noContainersRunningThatDependOn(it, stoppedContainers, context) }
                .forEach { stopContainer(it, context) }
    }

    private fun noContainersRunningThatDependOn(container: Container, stoppedContainers: Set<Container>, context: TaskEventContext): Boolean {
        val containersThatDependOnContainer = context.containersThatDependOn(container)

        return stoppedContainers.containsAll(containersThatDependOnContainer)
    }

    private fun stopContainer(container: Container, context: TaskEventContext) {
        val dockerContainer = context.getPastEventsOfType<ContainerCreatedEvent>()
                .single { it.container == container }
                .dockerContainer

        context.queueStep(StopContainerStep(container, dockerContainer))
    }

    override fun toString() = "${this::class.simpleName}(container: '${container.name}')"
}
