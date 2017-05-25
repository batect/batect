package decompose

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import decompose.config.Configuration
import decompose.config.ContainerMap
import decompose.config.TaskMap
import decompose.config.io.ConfigurationLoader
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object ApplicationSpec : Spek({
    describe("an application") {
        val outputStream = ByteArrayOutputStream()
        val errorStream = ByteArrayOutputStream()
        val configLoader = mock<ConfigurationLoader>()
        val taskRunner = mock<TaskRunner>()

        val dependencies = Kodein {
            bind<ConfigurationLoader>() with instance(configLoader)
            bind<TaskRunner>() with instance(taskRunner)
            bind<PrintStream>(PrintStreamType.Error) with instance(PrintStream(errorStream))
        }

        val application = Application(dependencies)

        beforeEachTest {
            outputStream.reset()
            errorStream.reset()

            reset(configLoader)
            reset(taskRunner)
        }

        given("no command-line arguments") {
            val args = emptyArray<String>()

            on("running") {
                val exitCode = application.run(args)

                it("prints a help message to the error stream") {
                    assert.that(errorStream.toString().trim(), equalTo("Usage: decompose [configuration file] [task name]"))
                }

                it("does not print anything to the output stream") {
                    assert.that(outputStream.toString(), equalTo(""))
                }

                it("returns a non-zero exit code") {
                    assert.that(exitCode, !equalTo(0))
                }
            }
        }

        given("only one command-line argument") {
            val args = arrayOf("config.yml")

            on("running") {
                val exitCode = application.run(args)

                it("prints a help message to the error stream") {
                    assert.that(errorStream.toString().trim(), equalTo("Usage: decompose [configuration file] [task name]"))
                }

                it("does not print anything to the output stream") {
                    assert.that(outputStream.toString(), equalTo(""))
                }

                it("returns a non-zero exit code") {
                    assert.that(exitCode, !equalTo(0))
                }
            }
        }

        given("a configuration file path and a task name") {
            val configFileName = "config.yml"
            val taskName = "the_task"
            val args = arrayOf(configFileName, taskName)

            on("running") {
                val config = Configuration("the_project", TaskMap(), ContainerMap())
                whenever(configLoader.loadConfig(configFileName)).thenReturn(config)

                val expectedTaskExitCode = 123
                whenever(taskRunner.run(config, taskName)).thenReturn(expectedTaskExitCode)

                val exitCode = application.run(args)

                it("does not print anything to the error stream") {
                    assert.that(errorStream.toString(), equalTo(""))
                }

                it("does not print anything to the output stream") {
                    assert.that(outputStream.toString(), equalTo(""))
                }

                it("runs the task") {
                    verify(taskRunner).run(config, taskName)
                }

                it("returns the exit code of the task") {
                    assert.that(exitCode, equalTo(expectedTaskExitCode))
                }
            }

            on("failing to load the configuration file") {
                val exception = RuntimeException("Could not load configuration for some reason.")
                whenever(configLoader.loadConfig(configFileName)).thenThrow(exception)

                val exitCode = application.run(args)

                it("prints the exception message to the error stream") {
                    assert.that(errorStream.toString().trim(), containsSubstring("Could not load configuration for some reason."))
                }

                it("does not print anything to the output stream") {
                    assert.that(outputStream.toString(), equalTo(""))
                }

                it("returns a non-zero exit code") {
                    assert.that(exitCode, !equalTo(0))
                }
            }

            on("failing to run the task") {
                val config = Configuration("the_project", TaskMap(), ContainerMap())
                whenever(configLoader.loadConfig(configFileName)).thenReturn(config)

                val exception = RuntimeException("Could not run task for some reason.")
                whenever(taskRunner.run(config, taskName)).thenThrow(exception)

                val exitCode = application.run(args)

                it("prints the exception message to the error stream") {
                    assert.that(errorStream.toString().trim(), containsSubstring("Could not run task for some reason."))
                }

                it("does not print anything to the output stream") {
                    assert.that(outputStream.toString(), equalTo(""))
                }

                it("returns a non-zero exit code") {
                    assert.that(exitCode, !equalTo(0))
                }
            }
        }
    }
})
