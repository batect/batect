package decompose

import decompose.config.Container
import decompose.docker.DockerContainer
import decompose.docker.DockerImage
import java.util.LinkedList
import java.util.Queue

class TaskStateMachine(val graph: DependencyGraph) {
    private val stepQueue: Queue<TaskStep> = LinkedList<TaskStep>()
    private val dockerContainers = mutableMapOf<Container, DockerContainer>()
    private var exitCode: Int? = null

    init {
        stepQueue.add(BeginTaskStep)
    }

    fun popNextStep(): TaskStep? = stepQueue.poll()

    fun processEvent(event: TaskEvent) {
        val newStep = when (event) {
            is TaskStartedEvent -> BuildImageStep(graph.taskContainerNode.container)
            is ImageBuiltEvent -> CreateContainerStep(event.container, event.image)
            is ImageBuildFailedEvent -> DisplayTaskFailureStep("Could not build image for container '${event.container.name}': ${event.message}")
            is ContainerCreatedEvent -> {
                dockerContainers[event.container] = event.dockerContainer
                RunContainerStep(event.container, event.dockerContainer)
            }
            is ContainerCreationFailedEvent -> DisplayTaskFailureStep("Could not create Docker container for container '${event.container.name}': ${event.message}")
            is ContainerExitedEvent -> {
                exitCode = event.exitCode
                RemoveContainerStep(event.container, dockerContainers[event.container]!!)
            }
            is ContainerRunFailedEvent -> {
                val dockerContainer = dockerContainers[event.container]!!
                DisplayTaskFailureStep("Could not run container '${event.container.name}': ${event.message}\n\n" +
                        "This container has not been removed, so you may need to clean up this container yourself by running 'docker rm --force ${dockerContainer.id}'.")
            }
            is ContainerRemovedEvent -> FinishTaskStep(exitCode!!)
            is ContainerRemovalFailedEvent -> {
                val dockerContainer = dockerContainers[event.container]!!
                DisplayTaskFailureStep("After the task completed with exit code ${exitCode!!}, the container '${event.container.name}' could not be removed: ${event.message}\n\n" +
                        "This container may not have been removed, so you may need to clean up this container yourself by running 'docker rm --force ${dockerContainer.id}'.")
            }
        }

        stepQueue.add(newStep)
    }
}

sealed class TaskStep
object BeginTaskStep : TaskStep()
data class BuildImageStep(val container: Container) : TaskStep()
data class CreateContainerStep(val container: Container, val image: DockerImage) : TaskStep()
data class RunContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep()
data class RemoveContainerStep(val container: Container, val dockerContainer: DockerContainer) : TaskStep()
data class FinishTaskStep(val exitCode: Int) : TaskStep()
data class DisplayTaskFailureStep(val message: String) : TaskStep()

sealed class TaskEvent
object TaskStartedEvent : TaskEvent()
data class ImageBuiltEvent(val container: Container, val image: DockerImage) : TaskEvent()
data class ImageBuildFailedEvent(val container: Container, val message: String) : TaskEvent()
data class ContainerCreatedEvent(val container: Container, val dockerContainer: DockerContainer) : TaskEvent()
data class ContainerCreationFailedEvent(val container: Container, val message: String) : TaskEvent()
data class ContainerExitedEvent(val container: Container, val exitCode: Int) : TaskEvent()
data class ContainerRunFailedEvent(val container: Container, val message: String) : TaskEvent()
data class ContainerRemovedEvent(val container: Container) : TaskEvent()
data class ContainerRemovalFailedEvent(val container: Container, val message: String) : TaskEvent()
