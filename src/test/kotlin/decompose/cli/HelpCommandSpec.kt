package decompose.cli

import com.github.salomonbrys.kodein.Kodein
import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object HelpCommandSpec : Spek({
    describe("a help command") {
        val parser = CommandLineParser(Kodein {})
        val doStuffCommand = NullCommand()

        parser.addCommandDefinition(object : CommandDefinition("do-stuff", "Do the thing.") {
            override fun createCommand(kodein: Kodein): Command = doStuffCommand
        })

        given("no command name to show help for") {
            val output = ByteArrayOutputStream()
            val outputStream = PrintStream(output)
            val command = HelpCommand(null, parser, outputStream)
            val exitCode = command.run()

            it("prints help information") {
                assert.that(output.toString(), equalTo("""
                            |Usage: decompose [COMMON OPTIONS] COMMAND [COMMAND OPTIONS]
                            |
                            |Commands:
                            |  do-stuff    Do the thing.
                            |  help        Print this help information and exit.
                            |
                            |For help on the options available for a command, run 'decompose help <command>'.
                            |
                            """.trimMargin()))
            }

            it("returns a non-zero exit code") {
                assert.that(exitCode, !equalTo(0))
            }
        }

        given("a command name to show help for") {
            given("and that command name is a valid command name") {
                val output = ByteArrayOutputStream()
                val outputStream = PrintStream(output)
                val command = HelpCommand("do-stuff", parser, outputStream)
                val exitCode = command.run()

                it("prints help information") {
                    assert.that(output.toString(), equalTo("""
                        |Usage: decompose [COMMON OPTIONS] do-stuff
                        |
                        |Do the thing.
                        |
                        |This command does not take any options.
                        |
                        """.trimMargin()))
                }

                it("returns a non-zero exit code") {
                    assert.that(exitCode, !equalTo(0))
                }
            }

            given("and that command name is not a valid command name") {
                val output = ByteArrayOutputStream()
                val outputStream = PrintStream(output)
                val command = HelpCommand("unknown-command", parser, outputStream)
                val exitCode = command.run()

                it("prints an error message") {
                    assert.that(output.toString(), equalTo("Invalid command 'unknown-command'. Run 'decompose help' for a list of valid commands.\n"))
                }

                it("returns a non-zero exit code") {
                    assert.that(exitCode, !equalTo(0))
                }
            }
        }
    }
})
