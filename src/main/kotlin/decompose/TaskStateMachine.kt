package decompose

import decompose.config.Container
import decompose.docker.DockerContainer
import decompose.docker.DockerImage
import decompose.docker.DockerNetwork
import java.util.LinkedList
import java.util.Queue

class TaskStateMachine(val graph: DependencyGraph) {
    private val stepQueue: Queue<TaskStep> = LinkedList<TaskStep>()
    private val dockerImages = mutableMapOf<Container, DockerImage>()
    private val dockerContainers = mutableMapOf<Container, DockerContainer>()
    private val containersLeftToStop = mutableSetOf<Container>()
    private val containersLeftToRemove = mutableSetOf<Container>()
    private val healthyContainers = mutableSetOf<Container>()
    private var exitCode: Int? = null
    private var taskNetwork: DockerNetwork? = null

    init {
        stepQueue.add(BeginTaskStep)
    }

    fun popNextStep(): TaskStep? = stepQueue.poll()
    fun peekNextSteps(): Iterable<TaskStep> = stepQueue

    fun processEvent(event: TaskEvent) {
        val newSteps = when (event) {
            is TaskStartedEvent -> handleTaskStartedEvent()
            is TaskNetworkCreatedEvent -> handleTaskNetworkCreatedEvent(event)
            is TaskNetworkCreationFailedEvent -> handleTaskNetworkCreationFailedEvent(event)
            is ImageBuiltEvent -> handleImageBuiltEvent(event)
            is ImageBuildFailedEvent -> handleImageBuildFailedEvent(event)
            is ContainerCreatedEvent -> handleContainerCreatedEvent(event)
            is ContainerCreationFailedEvent -> handleContainerCreationFailedEvent(event)
            is ContainerStartedEvent -> handleContainerStartedEvent(event)
            is ContainerStoppedEvent -> handleContainerStoppedEvent(event)
            is ContainerBecameHealthyEvent -> handleContainerBecameHealthyEvent(event)
            is ContainerExitedEvent -> handleContainerExitedEvent(event)
            is ContainerRunFailedEvent -> handleContainerRunFailedEvent(event)
            is ContainerRemovedEvent -> handleContainerRemovedEvent(event)
            is ContainerRemovalFailedEvent -> handleContainerRemovalFailedEvent(event)
            is TaskNetworkDeletedEvent -> handleTaskNetworkDeletedEvent()
            is TaskNetworkDeletionFailedEvent -> handleTaskNetworkDeletionFailedEvent(event)
        }

        stepQueue.addAll(newSteps)
    }

    private fun handleTaskStartedEvent() =
            graph.allNodes.map { BuildImageStep(it.container) } + CreateTaskNetworkStep

    private fun handleTaskNetworkCreatedEvent(event: TaskNetworkCreatedEvent): List<TaskStep> {
        taskNetwork = event.network

        return dockerImages.map { (container, image) -> CreateContainerStep(container, image, event.network) }
    }

    private fun handleTaskNetworkCreationFailedEvent(event: TaskNetworkCreationFailedEvent): List<TaskStep> {
        stepQueue.clear()

        return listOf(DisplayTaskFailureStep("Could not create network for task: ${event.message}"))
    }

    private fun handleImageBuiltEvent(event: ImageBuiltEvent): List<TaskStep> {
        dockerImages[event.container] = event.image

        if (taskNetwork == null) {
            return emptyList()
        } else {
            return listOf(CreateContainerStep(event.container, event.image, taskNetwork!!))
        }
    }

    private fun handleImageBuildFailedEvent(event: ImageBuildFailedEvent) =
            listOf(DisplayTaskFailureStep("Could not build image for container '${event.container.name}': ${event.message}"))

    private fun handleContainerCreatedEvent(event: ContainerCreatedEvent): List<TaskStep> {
        dockerContainers[event.container] = event.dockerContainer
        containersLeftToRemove.add(event.container)
        return startOrRunContainerIfReady(event.container)
    }

    private fun handleContainerCreationFailedEvent(event: ContainerCreationFailedEvent) =
            listOf(DisplayTaskFailureStep("Could not create Docker container for container '${event.container.name}': ${event.message}"))

    private fun handleContainerStartedEvent(event: ContainerStartedEvent) =
            listOf(WaitForContainerToBecomeHealthyStep(event.container, dockerContainers[event.container]!!))

    private fun handleContainerStoppedEvent(event: ContainerStoppedEvent) =
            handleContainerStopped(event.container)

    private fun handleContainerBecameHealthyEvent(event: ContainerBecameHealthyEvent): List<TaskStep> {
        healthyContainers.add(event.container)

        return graph.nodeFor(event.container).dependedOnBy
                .flatMap { startOrRunContainerIfReady(it.container) }
    }

    private fun handleContainerExitedEvent(event: ContainerExitedEvent): List<TaskStep> {
        if (event.container != graph.taskContainerNode.container) {
            throw IllegalArgumentException("Container '${event.container.name}' is not the task container.")
        }

        exitCode = event.exitCode

        return handleContainerStopped(event.container)
    }

    private fun handleContainerRunFailedEvent(event: ContainerRunFailedEvent): List<DisplayTaskFailureStep> {
        val dockerContainer = dockerContainers[event.container]!!
        return listOf(DisplayTaskFailureStep("Could not run container '${event.container.name}': ${event.message}\n\n" +
                "This container has not been removed, so you need to clean up this container yourself by running '${containerCleanupCommand(dockerContainer)}'.\n" +
                networkCleanupMessage()))
    }

    private fun handleContainerRemovedEvent(event: ContainerRemovedEvent): List<TaskStep> {
        containersLeftToRemove.remove(event.container)

        if (containersLeftToRemove.isEmpty()) {
            return listOf(DeleteTaskNetworkStep(taskNetwork!!))
        } else {
            return emptyList()
        }
    }

    private fun handleContainerRemovalFailedEvent(event: ContainerRemovalFailedEvent): List<DisplayTaskFailureStep> {
        val dockerContainer = dockerContainers[event.container]!!
        return listOf(DisplayTaskFailureStep("After the task completed with exit code ${exitCode!!}, the container '${event.container.name}' could not be removed: ${event.message}\n\n" +
                "This container may not have been removed, so you may need to clean up this container yourself by running '${containerCleanupCommand(dockerContainer)}'.\n" +
                networkCleanupMessage()))
    }

    private fun handleTaskNetworkDeletedEvent() = listOf(FinishTaskStep(exitCode!!))

    private fun handleTaskNetworkDeletionFailedEvent(event: TaskNetworkDeletionFailedEvent) =
            listOf(DisplayTaskFailureStep("After the task completed with exit code ${exitCode!!}, the network '${taskNetwork!!.id}' could not be deleted: ${event.message}\n\n" +
                    "This network may not have been removed, so you may need to clean up this network yourself by running '${networkCleanupCommand()}'."))

    private fun containerCleanupCommand(container: DockerContainer): String = "docker rm --force ${container.id}"
    private fun networkCleanupMessage(): String = "Furthermore, the network '${taskNetwork!!.id}' has not been removed, so you need to clean up this network yourself by running '${networkCleanupCommand()}'."
    private fun networkCleanupCommand(): String = "docker network rm ${taskNetwork!!.id}"

    private fun startOrRunContainerIfReady(container: Container): List<TaskStep> {
        val graphNode = graph.nodeFor(container)
        val dependencies = graphNode.dependsOnContainers
        val containerHasBeenCreated = dockerContainers.containsKey(container)
        val dependenciesAreHealthy = healthyContainers.containsAll(dependencies)

        if (containerHasBeenCreated && dependenciesAreHealthy) {
            val dockerContainer = dockerContainers[container]!!
            containersLeftToStop.add(container)

            if (isTaskNode(container)) {
                return listOf(RunContainerStep(container, dockerContainer))
            } else {
                return listOf(StartContainerStep(container, dockerContainer))
            }
        }

        return emptyList()
    }

    private fun isTaskNode(container: Container) = container == graph.taskContainerNode.container

    private fun handleContainerStopped(container: Container): List<TaskStep> {
        containersLeftToStop.remove(container)

        val containersToStop = graph.nodeFor(container)
                .dependsOnContainers
                .filterNot { isAnyContainerStillRunningThatDependsOn(it) }

        val stopSteps = containersToStop.map { StopContainerStep(it, dockerContainers[it]!!) }

        return stopSteps + listOf(RemoveContainerStep(container, dockerContainers[container]!!))
    }

    private fun isAnyContainerStillRunningThatDependsOn(container: Container): Boolean {
        val node = graph.nodeFor(container)
        val containersThatDependOnThisCandidate = node.dependedOnByContainers

        return containersThatDependOnThisCandidate.any { containersLeftToStop.contains(it) }
    }
}

sealed class TaskStep
object BeginTaskStep : TaskStep()
data class BuildImageStep(val container: Container) : TaskStep()
object CreateTaskNetworkStep : TaskStep()
data class CreateContainerStep(val container: Container, val image: DockerImage, val network: DockerNetwork) : TaskStep()
data class RunContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep()
data class StartContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep()
data class StopContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep()
data class RemoveContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep()
data class WaitForContainerToBecomeHealthyStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep()
data class FinishTaskStep(val exitCode: Int) : TaskStep()
data class DeleteTaskNetworkStep(val network: DockerNetwork) : TaskStep()
data class DisplayTaskFailureStep(val message: String) : TaskStep()

sealed class TaskEvent
object TaskStartedEvent : TaskEvent()
data class TaskNetworkCreatedEvent(val network: DockerNetwork) : TaskEvent()
data class TaskNetworkCreationFailedEvent(val message: String) : TaskEvent()
data class ImageBuiltEvent(val container: Container, val image: DockerImage) : TaskEvent()
data class ImageBuildFailedEvent(val container: Container, val message: String) : TaskEvent()
data class ContainerCreatedEvent(val container: Container, val dockerContainer: DockerContainer) : TaskEvent()
data class ContainerCreationFailedEvent(val container: Container, val message: String) : TaskEvent()
data class ContainerExitedEvent(val container: Container, val exitCode: Int) : TaskEvent()
data class ContainerStartedEvent(val container: Container) : TaskEvent()
data class ContainerStoppedEvent(val container: Container) : TaskEvent()
data class ContainerBecameHealthyEvent(val container: Container) : TaskEvent()
data class ContainerRunFailedEvent(val container: Container, val message: String) : TaskEvent()
object TaskNetworkDeletedEvent : TaskEvent()
data class TaskNetworkDeletionFailedEvent(val message: String) : TaskEvent()
data class ContainerRemovedEvent(val container: Container) : TaskEvent()
data class ContainerRemovalFailedEvent(val container: Container, val message: String) : TaskEvent()
