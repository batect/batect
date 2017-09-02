package batect.cli.commands

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.instance
import batect.TaskRunner
import batect.cli.Command
import batect.cli.CommandDefinition
import batect.cli.CommonOptions
import batect.cli.RequiredPositionalParameter
import batect.config.io.ConfigurationLoader

class RunTaskCommandDefinition : CommandDefinition("run", "Run a task.") {
    val taskName: String by RequiredPositionalParameter("TASK", "The name of the task to run.")

    override fun createCommand(kodein: Kodein): Command = RunTaskCommand(
            kodein.instance(CommonOptions.ConfigurationFileName),
            taskName,
            kodein.instance(),
            kodein.instance())
}

data class RunTaskCommand(
        val configFile: String,
        val taskName: String,
        val configLoader: ConfigurationLoader,
        val taskRunner: TaskRunner) : Command {

    override fun run(): Int {
        val config = configLoader.loadConfig(configFile)

        return taskRunner.run(config, taskName)
    }
}
