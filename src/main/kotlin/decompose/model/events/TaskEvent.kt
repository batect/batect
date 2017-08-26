package decompose.model.events

abstract class TaskEvent {
    abstract fun apply(context: TaskEventContext)
}
