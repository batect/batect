package decompose.cli

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.isEmptyString
import com.natpryce.hamkrest.throws
import decompose.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object CommandLineParserSpec : Spek({
    describe("a command line parser") {
        val output = ByteArrayOutputStream()
        val outputStream = PrintStream(output)

        beforeEachTest {
            output.reset()
        }

        given("a parser with no options or commands configured") {
            val parser = CommandLineParser(outputStream)

            describe("parsing an empty list of arguments") {
                var result: CommandLineParsingResult? = null

                beforeEachTest {
                    result = parser.parse(emptyList())
                }

                it("indicates that parsing failed") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Failed))
                }

                it("prints an error message to the output") {
                    assert.that(output.toString(), equalTo("No command specified. Run 'decompose help' for a list of valid commands.\n"))
                }
            }

            describe("parsing an unknown single argument that looks like a command") {
                var result: CommandLineParsingResult? = null

                beforeEachTest {
                    result = parser.parse(listOf("something"))
                }

                it("indicates that parsing failed") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Failed))
                }

                it("prints an error message to the output") {
                    assert.that(output.toString(), equalTo("Invalid command 'something'. Run 'decompose help' for a list of valid commands.\n"))
                }
            }

            describe("parsing an unknown single argument that looks like an option") {
                var result: CommandLineParsingResult? = null

                beforeEachTest {
                    result = parser.parse(listOf("--something"))
                }

                it("indicates that parsing failed") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Failed))
                }

                it("prints an error message to the output") {
                    assert.that(output.toString(), equalTo("Invalid option '--something'. Run 'decompose help' for a list of valid options.\n"))
                }
            }

            listOf("help", "--help").forEach { argument ->
                describe("parsing a list of arguments with just '$argument'") {
                    var result: CommandLineParsingResult? = null

                    beforeEachTest {
                        result = parser.parse(listOf(argument))
                    }

                    it("indicates that parsing succeeded") {
                        assert.that(result!!, isA<Succeeded>())
                    }

                    it("does not print anything to the output") {
                        assert.that(output.toString(), isEmptyString)
                    }

                    on("running the command returned") {
                        val exitCode = (result as Succeeded).command.run()

                        it("prints help information") {
                            assert.that(output.toString(), equalTo("""
                            |Usage: decompose [COMMON OPTIONS] COMMAND [COMMAND OPTIONS]
                            |
                            |Commands:
                            |  help    Print this help information and exit.
                            |
                            |For help on the options available for a command, run 'decompose help <command>'.
                            |
                            """.trimMargin()))
                        }

                        it("returns a non-zero exit code") {
                            assert.that(exitCode, !equalTo(0))
                        }
                    }
                }
            }
        }

        given("a parser with a single command configured") {
            val rootParser = CommandLineParser(outputStream)
            val command = CommandLineCommand("do-stuff", "Do the thing.", aliases = setOf("do-stuff-alias"))
            rootParser.addCommand(command)

            describe("parsing a list of arguments with just that command's name") {
                var result: CommandLineParsingResult? = null

                beforeEachTest {
                    result = rootParser.parse(listOf("do-stuff"))
                }

                it("indicates that parsing succeeded and returns the command") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Succeeded(command)))
                }

                it("does not print anything to the output") {
                    assert.that(output.toString(), isEmptyString)
                }
            }

            listOf("help", "--help").forEach { argument ->
                describe("parsing a list of arguments with just '$argument'") {
                    var result: CommandLineParsingResult? = null

                    beforeEachTest {
                        result = rootParser.parse(listOf(argument))
                    }

                    it("indicates that parsing succeeded") {
                        assert.that(result!!, isA<Succeeded>())
                    }

                    it("does not print anything to the output") {
                        assert.that(output.toString(), isEmptyString)
                    }

                    on("running the command returned") {
                        val exitCode = (result as Succeeded).command.run()

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
                }
            }

            on("attempting to add a new command with the same name") {
                val newCommand = CommandLineCommand("do-stuff", "The other do-stuff.")

                it("throws an exception") {
                    assert.that({ rootParser.addCommand(newCommand) }, throws(withMessage("A command with the name or alias 'do-stuff' is already registered.")))
                }
            }

            on("attempting to add a new command with the same alias") {
                val newCommand = CommandLineCommand("do-something-else", "The other do-stuff.", aliases = setOf("do-stuff-alias"))

                it("throws an exception") {
                    assert.that({ rootParser.addCommand(newCommand) }, throws(withMessage("A command with the name or alias 'do-stuff-alias' is already registered.")))
                }
            }

            on("attempting to add a new command with an alias with the same name as the existing command") {
                val newCommand = CommandLineCommand("do-other-stuff", "The other do-stuff.", aliases = setOf("do-stuff"))

                it("throws an exception") {
                    assert.that({ rootParser.addCommand(newCommand) }, throws(withMessage("A command with the name or alias 'do-stuff' is already registered.")))
                }
            }

            on("attempting to add a new command with a name that is the same as an existing command's alias") {
                val newCommand = CommandLineCommand("do-stuff-alias", "The other do-stuff.")

                it("throws an exception") {
                    assert.that({ rootParser.addCommand(newCommand) }, throws(withMessage("A command with the name or alias 'do-stuff-alias' is already registered.")))
                }
            }

            describe("parsing a list of arguments with that command's name") {
                var result: CommandLineParsingResult? = null

                beforeEachTest {
                    result = rootParser.parse(listOf("do-stuff"))
                }

                it("indicates that parsing succeeded and returns the command") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Succeeded(command)))
                }

                it("does not print anything to the output") {
                    assert.that(output.toString(), isEmptyString)
                }
            }

            describe("parsing the argument list 'help <command name>'") {
                var result: CommandLineParsingResult? = null

                beforeEachTest {
                    result = rootParser.parse(listOf("help", "do-stuff"))
                }

                it("indicates that parsing succeeded") {
                    assert.that(result!!, isA<Succeeded>())
                }

                it("does not print anything to the output") {
                    assert.that(output.toString(), isEmptyString)
                }

                on("running the command returned") {
                    val exitCode = (result as Succeeded).command.run()

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
            }

            describe("parsing the argument list 'help <command name> extra-arg") {
                var result: CommandLineParsingResult? = null

                beforeEachTest {
                    result = rootParser.parse(listOf("help", "do-stuff", "extra-arg"))
                }

                it("indicates that parsing failed") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Failed))
                }

                it("prints an error message to the output") {
                    assert.that(output.toString(), equalTo("Command 'help' takes at most 1 argument(s).\n"))
                }
            }

            describe("parsing the argument list 'help unknown-command'") {
                var result: CommandLineParsingResult? = null

                beforeEachTest {
                    result = rootParser.parse(listOf("help", "unknown-command"))
                }

                it("indicates that parsing succeeded") {
                    assert.that(result!!, isA<Succeeded>())
                }

                it("does not print anything to the output") {
                    assert.that(output.toString(), isEmptyString)
                }

                on("running the command returned") {
                    val exitCode = (result as Succeeded).command.run()

                    it("prints an error message") {
                        assert.that(output.toString(), equalTo("Invalid command 'unknown-command'. Run 'decompose help' for a list of valid commands.\n"))
                    }

                    it("returns a non-zero exit code") {
                        assert.that(exitCode, !equalTo(0))
                    }
                }
            }
        }
    }
})
