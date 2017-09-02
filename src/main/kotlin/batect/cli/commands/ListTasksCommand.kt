package batect.cli.commands

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.instance
import batect.PrintStreamType
import batect.cli.Command
import batect.cli.CommandDefinition
import batect.cli.CommonOptions
import batect.config.io.ConfigurationLoader
import java.io.PrintStream

class ListTasksCommandDefinition : CommandDefinition("tasks", "List all tasks defined in the configuration file.") {
    override fun createCommand(kodein: Kodein): Command = ListTasksCommand(
            kodein.instance(CommonOptions.ConfigurationFileName),
            kodein.instance(),
            kodein.instance(PrintStreamType.Error))
}

data class ListTasksCommand(val configFile: String, val configLoader: ConfigurationLoader, val outputStream: PrintStream) : Command {
    override fun run(): Int {
        val config = configLoader.loadConfig(configFile)

        outputStream.println("Available tasks:")

        config.tasks.sortedBy { it.name }
                .forEach {
                    outputStream.print("- ")
                    outputStream.print(it.name)

                    if (it.description.isNotBlank()) {
                        outputStream.print(": ")
                        outputStream.print(it.description)
                    }

                    outputStream.println()
                }

        return 0
    }
}
