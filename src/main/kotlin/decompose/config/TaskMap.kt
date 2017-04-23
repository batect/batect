package decompose.config

data class TaskMap(val contents: Collection<Task>) : Map<String, Task> {
    constructor(vararg contents: Task) : this(contents.asList())

    init {
        val duplicatedTasks = contents
                .groupBy { it.name }
                .filter { it.value.size > 1 }
                .map { it.key }

        if (duplicatedTasks.isNotEmpty()) {
            throw IllegalArgumentException("Cannot create a TaskSet where a task name is used more than once. Duplicated task names: ${duplicatedTasks.joinToString(", ")}")
        }
    }

    private val implementation: Map<String, Task> = contents.associateBy { it.name }

    override val entries: Set<Map.Entry<String, Task>>
        get() = implementation.entries

    override val keys: Set<String>
        get() = implementation.keys

    override val values: Collection<Task>
        get() = implementation.values

    override val size: Int
        get() = implementation.size

    override fun containsKey(key: String): Boolean = implementation.containsKey(key)
    override fun containsValue(value: Task): Boolean = implementation.containsValue(value)
    override fun get(key: String): Task? = implementation.get(key)
    override fun isEmpty(): Boolean = implementation.isEmpty()
}
