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
            is ContainerCreatedEvent -> {
                dockerContainers[event.container] = event.dockerContainer
                RunContainerStep(event.container, event.dockerContainer)
            }
            is ContainerExitedEvent -> {
                exitCode = event.exitCode
                RemoveContainerStep(event.container, dockerContainers[event.container]!!)
            }
            is ContainerRemovedEvent -> FinishTaskStep(exitCode!!)
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

sealed class TaskEvent
object TaskStartedEvent : TaskEvent()
data class ImageBuiltEvent(val container: Container, val image: DockerImage) : TaskEvent()
data class ContainerCreatedEvent(val container: Container, val dockerContainer: DockerContainer) : TaskEvent()
data class ContainerExitedEvent(val container: Container, val exitCode: Int) : TaskEvent()
data class ContainerRemovedEvent(val container: Container) : TaskEvent()

