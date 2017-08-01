package decompose.cli

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import decompose.PrintStreamType
import decompose.config.Configuration
import decompose.config.ContainerMap
import decompose.config.Task
import decompose.config.TaskMap
import decompose.config.TaskRunConfiguration
import decompose.config.io.ConfigurationLoader
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object ListTasksCommandSpec : Spek({
    describe("a 'list tasks' command") {
        describe("command line interface") {
            val commandLine = ListTasksCommandDefinition()
            val configLoader = mock<ConfigurationLoader>()
            val outputStream = mock<PrintStream>()

            val kodein = Kodein {
                bind<ConfigurationLoader>() with instance(configLoader)
                bind<PrintStream>(PrintStreamType.Error) with instance(outputStream)
            }

            describe("when given one parameter") {
                val result = commandLine.parse(listOf("thefile.yml"), kodein)

                it("indicates that parsing succeeded") {
                    assert.that(result, isA<Succeeded>())
                }

                it("returns a command instance ready for use") {
                    assert.that((result as Succeeded).command, equalTo<Command>(ListTasksCommand("thefile.yml", configLoader, outputStream)))
                }
            }
        }

        describe("when invoked") {
            val configFile = "config.yml"
            val taskRunConfig = TaskRunConfiguration("some-container", "dont-care")
            val task1 = Task("first-task", taskRunConfig)
            val task2 = Task("other-task", taskRunConfig)
            val task3 = Task("another-task", taskRunConfig)
            val config = Configuration("the_project", TaskMap(task1, task2, task3), ContainerMap())

            val configLoader = mock<ConfigurationLoader> {
                on { loadConfig(configFile) } doReturn config
            }

            val output = ByteArrayOutputStream()
            val command = ListTasksCommand(configFile, configLoader, PrintStream(output))

            describe("when the configuration file can be loaded") {
                val exitCode = command.run()

                it("prints the names of the available tasks in alphabetical order") {
                    assert.that(output.toString(), equalTo("another-task\nfirst-task\nother-task\n"))
                }

                it("returns a zero exit code") {
                    assert.that(exitCode, equalTo(0))
                }
            }
        }
    }
})
