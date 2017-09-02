package batect.cli

class NullCommand : Command {
    override fun run(): Int = throw NotImplementedError()
}
