package decompose.model.events

import decompose.model.steps.RunContainerStep
import decompose.model.steps.StartContainerStep
import decompose.config.Container
import decompose.docker.DockerContainer

data class ContainerBecameHealthyEvent(val container: Container) : TaskEvent() {
    override fun apply(context: TaskEventContext) {
        if (context.isAborting) {
            return
        }

        val containersThatDependOnThisContainer = context.containersThatDependOn(container)
        val healthyContainers = context.getPastEventsOfType<ContainerBecameHealthyEvent>()
                .mapTo(mutableSetOf()) { it.container }

        containersThatDependOnThisContainer
                .filter { dependenciesAreHealthy(it, healthyContainers, context) }
                .associate { it to dockerContainerFor(it, context) }
                .filter { (_, dockerContainer) -> dockerContainer != null }
                .forEach { (container, dockerContainer) -> runContainer(container, dockerContainer!!, context) }
    }

    private fun dependenciesAreHealthy(container: Container, healthyContainers: Set<Container>, context: TaskEventContext): Boolean {
        val dependencies = context.dependenciesOf(container)

        return healthyContainers.containsAll(dependencies)
    }

    private fun dockerContainerFor(container: Container, context: TaskEventContext): DockerContainer? {
        val event = context.getPastEventsOfType<ContainerCreatedEvent>()
                .singleOrNull { it.container == container }

        return event?.dockerContainer
    }

    private fun runContainer(container: Container, dockerContainer: DockerContainer, context: TaskEventContext) {
        if (context.isTaskContainer(container)) {
            context.queueStep(RunContainerStep(container, dockerContainer))
        } else {
            context.queueStep(StartContainerStep(container, dockerContainer))
        }
    }

    override fun toString() = "${this::class.simpleName}(container: '${container.name}')"
}
