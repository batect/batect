package decompose.model.events

import decompose.TaskStep
import decompose.config.Container
import kotlin.reflect.KClass

// TODO: do we need to provide higher-level methods (eg. getDockerContainerForContainer())?
// Seems like there could be a performance win to be had there because we can optimise for these
// common queries.
interface TaskEventContext {
    fun queueStep(step: TaskStep)
    fun abort()

    fun <T : TaskEvent> getPastEventsOfType(clazz: KClass<T>): Set<T>
    fun <T : TaskEvent> getSinglePastEventOfType(clazz: KClass<T>): T?

    fun <T : TaskStep> removePendingStepsOfType(clazz: KClass<T>)
    fun <T : TaskStep> getPendingAndProcessedStepsOfType(clazz: KClass<T>): Set<T>
    fun <T : TaskStep> getProcessedStepsOfType(clazz: KClass<T>): Set<T>

    fun isTaskContainer(container: Container): Boolean
    fun dependenciesOf(container: Container): Set<Container>
    fun containersThatDependOn(container: Container): Set<Container>

    val allContainers: Set<Container>
    val isAborting: Boolean
}

inline fun <reified T: TaskEvent> TaskEventContext.getPastEventsOfType(): Set<T> = this.getPastEventsOfType(T::class)
inline fun <reified T: TaskEvent> TaskEventContext.getSinglePastEventOfType(): T? = this.getSinglePastEventOfType(T::class)
inline fun <reified T: TaskStep> TaskEventContext.removePendingStepsOfType() = this.removePendingStepsOfType(T::class)
inline fun <reified T: TaskStep> TaskEventContext.getPendingAndProcessedStepsOfType(): Set<T> = this.getPendingAndProcessedStepsOfType(T::class)
inline fun <reified T: TaskStep> TaskEventContext.getProcessedStepsOfType(): Set<T> = this.getProcessedStepsOfType(T::class)
