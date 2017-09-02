package batect

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import batect.cli.Command
import batect.cli.CommandLineParser
import batect.cli.Failed
import batect.cli.Succeeded
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object ApplicationSpec : Spek({
    describe("an application") {
        val errorStream = ByteArrayOutputStream()
        val commandLineParser = mock<CommandLineParser>()

        val dependencies = Kodein {
            bind<PrintStream>(PrintStreamType.Error) with instance(PrintStream(errorStream))
            bind<CommandLineParser>() with instance(commandLineParser)
        }

        val application = Application(dependencies)
        val args = listOf("some-command", "some-param")

        beforeEachTest {
            errorStream.reset()

            reset(commandLineParser)
        }

        given("the command line parser returns a command") {
            on("running the application") {
                val command = object : Command {
                    override fun run(): Int = 123
                }

                whenever(commandLineParser.parse(args)).thenReturn(Succeeded(command))

                val exitCode = application.run(args)

                it("does not print anything to the error stream") {
                    assertThat(errorStream.toString(), equalTo(""))
                }

                it("returns the exit code from the command") {
                    assertThat(exitCode, equalTo(123))
                }
            }
        }

        given("the command line parser returns an error") {
            on("running the application") {
                whenever(commandLineParser.parse(args)).thenReturn(Failed("Something went wrong while parsing arguments"))

                val exitCode = application.run(args)

                it("prints the error message to the error stream") {
                    assertThat(errorStream.toString(), equalTo("Something went wrong while parsing arguments\n"))
                }

                it("returns a non-zero exit code") {
                    assertThat(exitCode, !equalTo(0))
                }
            }
        }

        given("the command line parser throws an exception") {
            on("running the application") {
                whenever(commandLineParser.parse(args)).thenThrow(RuntimeException("Everything is broken"))

                val exitCode = application.run(args)

                it("prints the exception message to the error stream") {
                    assertThat(errorStream.toString(), containsSubstring("Everything is broken"))
                }

                it("returns a non-zero exit code") {
                    assertThat(exitCode, !equalTo(0))
                }
            }
        }

        given("the command throws an exception") {
            on("running the application") {
                val command = object : Command {
                    override fun run(): Int = throw RuntimeException("Everything is broken")
                }

                whenever(commandLineParser.parse(args)).thenReturn(Succeeded(command))

                val exitCode = application.run(args)

                it("prints the exception message to the error stream") {
                    assertThat(errorStream.toString(), containsSubstring("Everything is broken"))
                }

                it("returns a non-zero exit code") {
                    assertThat(exitCode, !equalTo(0))
                }
            }
        }
    }
})
