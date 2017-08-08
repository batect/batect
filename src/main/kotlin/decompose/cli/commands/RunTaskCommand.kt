package decompose.cli.commands

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.instance
import decompose.TaskRunner
import decompose.cli.Command
import decompose.cli.CommandDefinition
import decompose.cli.RequiredPositionalParameter
import decompose.config.io.ConfigurationLoader

class RunTaskCommandDefinition : CommandDefinition("run", "Run a task.") {
    val configFile: String by RequiredPositionalParameter("CONFIGFILE", "The configuration file to use.")
    val taskName: String by RequiredPositionalParameter("TASK", "The name of the task to run.")

    override fun createCommand(kodein: Kodein): Command = RunTaskCommand(configFile, taskName, kodein.instance(), kodein.instance())
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
