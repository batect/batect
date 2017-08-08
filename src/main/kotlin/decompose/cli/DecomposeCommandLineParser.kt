package decompose.cli

import com.github.salomonbrys.kodein.Kodein
import decompose.cli.commands.ListTasksCommandDefinition
import decompose.cli.commands.RunTaskCommandDefinition

class DecomposeCommandLineParser(kodein: Kodein) : CommandLineParser(kodein) {
    init {
        addCommandDefinition(RunTaskCommandDefinition())
        addCommandDefinition(ListTasksCommandDefinition())
    }

    override fun createBindings(): Kodein.Module {
        return super.createBindings()
    }
}
