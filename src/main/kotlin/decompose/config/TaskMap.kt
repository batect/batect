package decompose.config

class TaskMap(contents: Iterable<Task>) : NamedObjectMap<Task>("task", contents) {
    constructor(vararg contents: Task) : this(contents.asIterable())

    override fun nameFor(value: Task): String = value.name
}
