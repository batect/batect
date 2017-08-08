package decompose.cli.commands

import com.github.salomonbrys.kodein.Kodein
import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import decompose.cli.Command
import decompose.cli.CommandDefinition
import decompose.cli.CommandLineParser
import decompose.cli.NullCommand
import decompose.cli.OptionDefinition
import decompose.cli.OptionalPositionalParameter
import decompose.cli.RequiredPositionalParameter
import decompose.cli.ValueOption
import decompose.cli.ValueOptionWithDefault
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
                    on { getCommonOptions() } doReturn emptySet()
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
                    on { getCommonOptions() } doReturn setOf<OptionDefinition>(
                            ValueOption("awesomeness-level", "Level of awesomeness to use."),
                            ValueOption("booster-level", "Level of boosters to use."),
                            ValueOption("file", "File name to use.", 'f'),
                            ValueOptionWithDefault("sensible-default", "Something you can override if you want.", "the-default-value")
                    )
                }

                val command = HelpCommand(null, parser, outputStream)
                val exitCode = command.run()

                it("prints help information") {
                    assert.that(output.toString(), equalTo("""
                        |Usage: decompose [COMMON OPTIONS] COMMAND [COMMAND OPTIONS]
                        |
                        |Commands:
                        |  do-other-stuff                   Do the other thing.
                        |  do-stuff                         Do the thing.
                        |
                        |Common options:
                        |      --awesomeness-level=value    Level of awesomeness to use.
                        |      --booster-level=value        Level of boosters to use.
                        |  -f, --file=value                 File name to use.
                        |      --sensible-default=value     Something you can override if you want. (defaults to 'the-default-value' if not set)
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
                given("the root parser has some common options") {
                    val commonOptions = setOf(ValueOption("some-option", "Some common option."))

                    given("and that command has no options or positional parameters") {
                        val output = ByteArrayOutputStream()
                        val outputStream = PrintStream(output)
                        val parser = mock<CommandLineParser> {
                            on { getCommandDefinitionByName("do-stuff") } doReturn firstCommandDefinition
                            on { getCommonOptions() } doReturn commonOptions
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
                            |For help on the common options available for all commands, run 'decompose help'.
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
                            on { getCommonOptions() } doReturn commonOptions
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
                            |For help on the common options available for all commands, run 'decompose help'.
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
                            on { getCommonOptions() } doReturn commonOptions
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
                            |For help on the common options available for all commands, run 'decompose help'.
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
                            on { getCommonOptions() } doReturn commonOptions
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
                            |For help on the common options available for all commands, run 'decompose help'.
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
                            on { getCommonOptions() } doReturn commonOptions
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
                            |For help on the common options available for all commands, run 'decompose help'.
                            |
                            |""".trimMargin()))
                        }

                        it("returns a non-zero exit code") {
                            assert.that(exitCode, !equalTo(0))
                        }
                    }
                }

                given("the root parser has no options") {
                    val output = ByteArrayOutputStream()
                    val outputStream = PrintStream(output)
                    val parser = mock<CommandLineParser> {
                        on { getCommandDefinitionByName("do-stuff") } doReturn firstCommandDefinition
                    }

                    val command = HelpCommand("do-stuff", parser, outputStream)
                    val exitCode = command.run()

                    it("does not include the placeholder for common options in the header or print a message about common options") {
                        assert.that(output.toString(), equalTo("""
                            |Usage: decompose do-stuff
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
