package batect.model.events

interface TaskEventSink {
    fun postEvent(event: TaskEvent)
}
