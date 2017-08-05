package decompose.cli

import com.github.salomonbrys.kodein.Kodein
import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object HelpCommandSpec : Spek({
    describe("a help command") {
        val firstCommandDefinition = object : CommandDefinition("do-stuff", "Do the thing.") {
            override fun createCommand(kodein: Kodein): Command = NullCommand()
        }

        val secondCommandDefinition = object : CommandDefinition("do-other-stuff", "Do the other thing.") {
            override fun createCommand(kodein: Kodein): Command = NullCommand()
        }

        given("no command name to show help for") {
            given("and the root parser has no common options") {
                val output = ByteArrayOutputStream()
                val outputStream = PrintStream(output)
                val parser = mock<CommandLineParser> {
                    on { getAllCommandDefinitions() } doReturn setOf(firstCommandDefinition, secondCommandDefinition)
                }

                val command = HelpCommand(null, parser, outputStream)
                val exitCode = command.run()

                it("prints help information") {
                    assert.that(output.toString(), equalTo("""
                        |Usage: decompose COMMAND [COMMAND OPTIONS]
                        |
                        |Commands:
                        |  do-other-stuff    Do the other thing.
                        |  do-stuff          Do the thing.
                        |
                        |For help on the options available for a command, run 'decompose help <command>'.
                        |
                        |""".trimMargin()))
                }

                it("returns a non-zero exit code") {
                    assert.that(exitCode, !equalTo(0))
                }
            }

            given("and the root parser has some common options") {
                val output = ByteArrayOutputStream()
                val outputStream = PrintStream(output)
                val parser = mock<CommandLineParser> {
                    on { getAllCommandDefinitions() } doReturn setOf(firstCommandDefinition, secondCommandDefinition)
                    on { getAllCommonOptions() } doReturn setOf(
                            OptionalOption("awesomeness-level", "Level of awesomeness to use."),
                            OptionalOption("booster-level", "Level of boosters to use.")
                    )
                }

                val command = HelpCommand(null, parser, outputStream)
                val exitCode = command.run()

                it("prints help information") {
                    assert.that(output.toString(), equalTo("""
                        |Usage: decompose [COMMON OPTIONS] COMMAND [COMMAND OPTIONS]
                        |
                        |Commands:
                        |  do-other-stuff               Do the other thing.
                        |  do-stuff                     Do the thing.
                        |
                        |Common options:
                        |  --awesomeness-level=value    Level of awesomeness to use.
                        |  --booster-level=value        Level of boosters to use.
                        |
                        |For help on the options available for a command, run 'decompose help <command>'.
                        |
                        |""".trimMargin()))
                }

                it("returns a non-zero exit code") {
                    assert.that(exitCode, !equalTo(0))
                }
            }
        }

        given("a command name to show help for") {
            given("and that command name is a valid command name") {
                given("and that command has no options or positional parameters") {
                    val output = ByteArrayOutputStream()
                    val outputStream = PrintStream(output)
                    val parser = mock<CommandLineParser> {
                        on { getCommandDefinitionByName("do-stuff") } doReturn firstCommandDefinition
                    }

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
                            |""".trimMargin()))
                    }

                    it("returns a non-zero exit code") {
                        assert.that(exitCode, !equalTo(0))
                    }
                }

                given("and that command has a single optional positional parameter") {
                    val output = ByteArrayOutputStream()
                    val outputStream = PrintStream(output)

                    val parser = mock<CommandLineParser> {
                        on { getCommandDefinitionByName("do-stuff") } doReturn object : CommandDefinition("do-stuff", "Do the thing.") {
                            val thingToDo: String? by OptionalPositionalParameter("THING", "Thing to do.")

                            override fun createCommand(kodein: Kodein): Command = NullCommand()
                        }
                    }

                    val command = HelpCommand("do-stuff", parser, outputStream)
                    val exitCode = command.run()

                    it("prints help information") {
                        assert.that(output.toString(), equalTo("""
                            |Usage: decompose [COMMON OPTIONS] do-stuff [THING]
                            |
                            |Do the thing.
                            |
                            |Parameters:
                            |  THING    (optional) Thing to do.
                            |
                            |""".trimMargin()))
                    }

                    it("returns a non-zero exit code") {
                        assert.that(exitCode, !equalTo(0))
                    }
                }

                given("and that command has a single required positional parameter") {
                    val output = ByteArrayOutputStream()
                    val outputStream = PrintStream(output)

                    val parser = mock<CommandLineParser> {
                        on { getCommandDefinitionByName("do-stuff") } doReturn object : CommandDefinition("do-stuff", "Do the thing.") {
                            val thingToDo: String by RequiredPositionalParameter("THING", "Thing to do.")

                            override fun createCommand(kodein: Kodein): Command = NullCommand()
                        }
                    }

                    val command = HelpCommand("do-stuff", parser, outputStream)
                    val exitCode = command.run()

                    it("prints help information") {
                        assert.that(output.toString(), equalTo("""
                            |Usage: decompose [COMMON OPTIONS] do-stuff THING
                            |
                            |Do the thing.
                            |
                            |Parameters:
                            |  THING    Thing to do.
                            |
                            |""".trimMargin()))
                    }

                    it("returns a non-zero exit code") {
                        assert.that(exitCode, !equalTo(0))
                    }
                }

                given("and that command has two required positional parameters") {
                    val output = ByteArrayOutputStream()
                    val outputStream = PrintStream(output)

                    val parser = mock<CommandLineParser> {
                        on { getCommandDefinitionByName("do-stuff") } doReturn object : CommandDefinition("do-stuff", "Do the thing.") {
                            val thingToDo: String by RequiredPositionalParameter("THING", "Thing to do.")
                            val otherThingToDo: String by RequiredPositionalParameter("OTHER-THING", "Other thing to do.")

                            override fun createCommand(kodein: Kodein): Command = NullCommand()
                        }
                    }

                    val command = HelpCommand("do-stuff", parser, outputStream)
                    val exitCode = command.run()

                    it("prints help information") {
                        assert.that(output.toString(), equalTo("""
                            |Usage: decompose [COMMON OPTIONS] do-stuff THING OTHER-THING
                            |
                            |Do the thing.
                            |
                            |Parameters:
                            |  THING          Thing to do.
                            |  OTHER-THING    Other thing to do.
                            |
                            |""".trimMargin()))
                    }

                    it("returns a non-zero exit code") {
                        assert.that(exitCode, !equalTo(0))
                    }
                }

                given("and that command has a required and an optional parameter") {
                    val output = ByteArrayOutputStream()
                    val outputStream = PrintStream(output)

                    val parser = mock<CommandLineParser> {
                        on { getCommandDefinitionByName("do-stuff") } doReturn object : CommandDefinition("do-stuff", "Do the thing.") {
                            val thingToDo: String by RequiredPositionalParameter("THING", "Thing to do.")
                            val otherThingToDo: String? by OptionalPositionalParameter("OTHER-THING", "Other thing to do.")

                            override fun createCommand(kodein: Kodein): Command = NullCommand()
                        }
                    }

                    val command = HelpCommand("do-stuff", parser, outputStream)
                    val exitCode = command.run()

                    it("prints help information") {
                        assert.that(output.toString(), equalTo("""
                            |Usage: decompose [COMMON OPTIONS] do-stuff THING [OTHER-THING]
                            |
                            |Do the thing.
                            |
                            |Parameters:
                            |  THING          Thing to do.
                            |  OTHER-THING    (optional) Other thing to do.
                            |
                            |""".trimMargin()))
                    }

                    it("returns a non-zero exit code") {
                        assert.that(exitCode, !equalTo(0))
                    }
                }
            }

            given("and that command name is not a valid command name") {
                val output = ByteArrayOutputStream()
                val outputStream = PrintStream(output)
                val parser = mock<CommandLineParser>()
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
