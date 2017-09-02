package batect.config

class ContainerMap(contents: Iterable<Container>) : NamedObjectMap<Container>("container", contents) {
    constructor(vararg contents: Container) : this(contents.asIterable())

    override fun nameFor(value: Container): String = value.name
}
