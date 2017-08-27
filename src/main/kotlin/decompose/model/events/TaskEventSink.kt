package decompose.model.events

interface TaskEventSink {
    fun postEvent(event: TaskEvent)
}
