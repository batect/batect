package decompose.cli.commands

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import decompose.TaskRunner
import decompose.cli.Command
import decompose.cli.CommonOptions
import decompose.cli.Succeeded
import decompose.config.Configuration
import decompose.config.ContainerMap
import decompose.config.TaskMap
import decompose.config.io.ConfigurationLoader
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

object RunTaskCommandSpec : Spek({
    describe("a 'run task' command") {
        describe("command line interface") {
            val commandLine = RunTaskCommandDefinition()
            val configLoader = mock<ConfigurationLoader>()
            val taskRunner = mock<TaskRunner>()

            val kodein = Kodein {
                bind<ConfigurationLoader>() with instance(configLoader)
                bind<TaskRunner>() with instance(taskRunner)
                bind<String>(CommonOptions.ConfigurationFileName) with instance("thefile.yml")
            }

            describe("when given one parameter") {
                val result = commandLine.parse(listOf("the-task"), kodein)

                it("indicates that parsing succeeded") {
                    assert.that(result, isA<Succeeded>())
                }

                it("returns a command instance ready for use") {
                    assert.that((result as Succeeded).command, equalTo<Command>(RunTaskCommand("thefile.yml", "the-task", configLoader, taskRunner)))
                }
            }
        }

        describe("when invoked") {
            val configFile = "config.yml"
            val taskName = "the_task"
            val config = Configuration("the_project", TaskMap(), ContainerMap())
            val expectedTaskExitCode = 123

            val configLoader = mock<ConfigurationLoader> {
                on { loadConfig(configFile) } doReturn config
            }

            val taskRunner = mock<TaskRunner> {
                on { run(config, taskName) } doReturn expectedTaskExitCode
            }

            val command = RunTaskCommand(configFile, taskName, configLoader, taskRunner)

            describe("when the configuration file can be loaded and the task runs successfully") {
                val exitCode = command.run()

                it("runs the task") {
                    verify(taskRunner).run(config, taskName)
                }

                it("returns the exit code of the task") {
                    assert.that(exitCode, equalTo(expectedTaskExitCode))
                }
            }
        }
    }
})
