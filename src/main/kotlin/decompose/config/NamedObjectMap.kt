package decompose.config

abstract class NamedObjectMap<E>(contentName: String, contents: Iterable<E>) : Map<String, E> {
    init {
        val duplicates = contents
                .groupBy { nameFor(it) }
                .filter { it.value.size > 1 }
                .map { it.key }

        if (duplicates.isNotEmpty()) {
            throw IllegalArgumentException("Cannot create a ${this.javaClass.simpleName} where a $contentName name is used more than once. Duplicated $contentName names: ${duplicates.joinToString(", ")}")
        }
    }

    private val implementation: Map<String, E> = contents.associateBy { nameFor(it) }

    override val entries: Set<Map.Entry<String, E>>
        get() = implementation.entries

    override val keys: Set<String>
        get() = implementation.keys

    override val values: Collection<E>
        get() = implementation.values

    override val size: Int
        get() = implementation.size

    override fun containsKey(key: String): Boolean = implementation.containsKey(key)
    override fun containsValue(value: E): Boolean = implementation.containsValue(value)
    override fun get(key: String): E? = implementation.get(key)
    override fun isEmpty(): Boolean = implementation.isEmpty()

    abstract fun nameFor(value: E): String
}
