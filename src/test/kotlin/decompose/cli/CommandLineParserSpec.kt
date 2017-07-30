package decompose.cli

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import com.natpryce.hamkrest.throws
import decompose.PrintStreamType
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
        given("a parser with no options or commands configured") {
            val outputStream = PrintStream(ByteArrayOutputStream())

            val injections = Kodein {
                bind<PrintStream>(PrintStreamType.Error) with instance(outputStream)
            }

            val parser = CommandLineParser(injections)

            describe("parsing an empty list of arguments") {
                val result: CommandLineParsingResult = parser.parse(emptyList())

                it("returns that parsing failed") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Failed("No command specified. Run 'decompose help' for a list of valid commands.")))
                }
            }

            describe("parsing an unknown single argument that looks like a command") {
                val result: CommandLineParsingResult = parser.parse(listOf("something"))

                it("indicates that parsing failed") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Failed("Invalid command 'something'. Run 'decompose help' for a list of valid commands.")))
                }
            }

            describe("parsing an unknown single argument that looks like an option") {
                val result: CommandLineParsingResult = parser.parse(listOf("--something"))

                it("indicates that parsing failed") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Failed("Invalid option '--something'. Run 'decompose help' for a list of valid options.")))
                }
            }

            listOf("help", "--help").forEach { argument ->
                describe("parsing a list of arguments with just '$argument'") {
                    val result: CommandLineParsingResult = parser.parse(listOf(argument))

                    it("indicates that parsing succeeded") {
                        assert.that(result, isA<Succeeded>())
                    }

                    it("returns a HelpCommand instance ready for use") {
                        assert.that((result as Succeeded).command, equalTo<Command>(HelpCommand(null, parser, outputStream)))
                    }
                }
            }
        }

        given("a parser with a single command that does not take any options or parameters") {
            val outputStream = PrintStream(ByteArrayOutputStream())

            val injections = Kodein {
                bind<PrintStream>(PrintStreamType.Error) with instance(outputStream)
            }

            val rootParser = CommandLineParser(injections)
            val command = NullCommand()

            val commandDefinition = object : CommandDefinition("do-stuff", "Do the thing.", aliases = setOf("do-stuff-alias")) {
                override fun createCommand(kodein: Kodein): Command = command
            }

            rootParser.addCommandDefinition(commandDefinition)

            describe("parsing a list of arguments with just that command's name") {
                val result: CommandLineParsingResult = rootParser.parse(listOf("do-stuff"))

                it("indicates that parsing succeeded and returns the command") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Succeeded(command)))
                }
            }

            describe("parsing a list of arguments with the command's name and an extra parameter") {
                val result: CommandLineParsingResult = rootParser.parse(listOf("do-stuff", "some value"))

                it("indicates that parsing failed") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Failed("Command 'do-stuff' does not take any parameters. ('some value' is the first extra parameter.)")))
                }
            }

            listOf("help", "--help").forEach { argument ->
                describe("parsing a list of arguments with just '$argument'") {
                    val result: CommandLineParsingResult = rootParser.parse(listOf(argument))

                    it("indicates that parsing succeeded") {
                        assert.that(result, isA<Succeeded>())
                    }

                    it("returns a HelpCommand instance ready for use") {
                        assert.that((result as Succeeded).command, equalTo<Command>(HelpCommand(null, rootParser, outputStream)))
                    }
                }
            }

            on("attempting to add a new command with the same name") {
                val newCommand = object : CommandDefinition("do-stuff", "The other do-stuff.") {
                    override fun createCommand(kodein: Kodein): Command = throw NotImplementedError()
                }

                it("throws an exception") {
                    assert.that({ rootParser.addCommandDefinition(newCommand) }, throws(withMessage("A command with the name or alias 'do-stuff' is already registered.")))
                }
            }

            on("attempting to add a new command with the same alias") {
                val newCommand = object : CommandDefinition("do-something-else", "The other do-stuff.", aliases = setOf("do-stuff-alias")) {
                    override fun createCommand(kodein: Kodein): Command = throw NotImplementedError()
                }

                it("throws an exception") {
                    assert.that({ rootParser.addCommandDefinition(newCommand) }, throws(withMessage("A command with the name or alias 'do-stuff-alias' is already registered.")))
                }
            }

            on("attempting to add a new command with an alias with the same name as the existing command") {
                val newCommand = object : CommandDefinition("do-other-stuff", "The other do-stuff.", aliases = setOf("do-stuff")) {
                    override fun createCommand(kodein: Kodein): Command = throw NotImplementedError()
                }

                it("throws an exception") {
                    assert.that({ rootParser.addCommandDefinition(newCommand) }, throws(withMessage("A command with the name or alias 'do-stuff' is already registered.")))
                }
            }

            on("attempting to add a new command with a name that is the same as an existing command's alias") {
                val newCommand = object : CommandDefinition("do-stuff-alias", "The other do-stuff.") {
                    override fun createCommand(kodein: Kodein): Command = throw NotImplementedError()
                }

                it("throws an exception") {
                    assert.that({ rootParser.addCommandDefinition(newCommand) }, throws(withMessage("A command with the name or alias 'do-stuff-alias' is already registered.")))
                }
            }

            describe("parsing the argument list 'help <command name>'") {
                val result: CommandLineParsingResult = rootParser.parse(listOf("help", "do-stuff"))

                it("indicates that parsing succeeded") {
                    assert.that(result, isA<Succeeded>())
                }

                it("returns a HelpCommand instance ready for use") {
                    assert.that((result as Succeeded).command, equalTo<Command>(HelpCommand("do-stuff", rootParser, outputStream)))
                }
            }

            describe("parsing the argument list 'help <command name> extra-arg") {
                val result: CommandLineParsingResult = rootParser.parse(listOf("help", "do-stuff", "extra-arg"))

                it("indicates that parsing failed") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Failed("Command 'help' takes at most 1 parameter. ('extra-arg' is the first extra parameter.)")))
                }
            }
        }

        given("a parser with a single command that takes a single required parameter") {
            val injections = Kodein { }
            val rootParser = CommandLineParser(injections)

            data class SingleStringParameterCommand(val value: String) : Command {
                override fun run(): Int = throw NotImplementedError()
            }

            val commandDefinition = object : CommandDefinition("do-stuff", "Do the thing.") {
                val thing: String by RequiredPositionalParameter("THING", "The thing.")

                override fun createCommand(kodein: Kodein): Command = SingleStringParameterCommand(thing)
            }

            rootParser.addCommandDefinition(commandDefinition)

            describe("parsing a list of arguments with just that command's name") {
                val result: CommandLineParsingResult = rootParser.parse(listOf("do-stuff"))

                it("indicates that parsing failed") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Failed("Command 'do-stuff' requires at least 1 parameter. The parameter 'THING' was not supplied.")))
                }
            }

            describe("parsing a list of arguments with the command's name and a value for the required parameter") {
                val result: CommandLineParsingResult = rootParser.parse(listOf("do-stuff", "some value"))

                it("indicates that parsing succeeded") {
                    assert.that(result, isA<Succeeded>())
                }

                it("returns a command instance ready for use") {
                    assert.that((result as Succeeded).command, equalTo<Command>(SingleStringParameterCommand("some value")))
                }
            }

            describe("parsing a list of arguments with the command's name, a value for the required parameter and an extra parameter") {
                val result: CommandLineParsingResult = rootParser.parse(listOf("do-stuff", "some value", "some extra value"))

                it("indicates that parsing failed") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Failed("Command 'do-stuff' takes at most 1 parameter. ('some extra value' is the first extra parameter.)")))
                }
            }
        }

        given("a parser with a single command that takes a required parameter and an optional parameter") {
            val injections = Kodein { }
            val rootParser = CommandLineParser(injections)

            data class TestCommand(val requiredValue: String, val optionalValue: String?) : Command {
                override fun run(): Int = throw NotImplementedError()
            }

            val commandDefinition = object : CommandDefinition("do-stuff", "Do the thing.") {
                val requiredValue: String by RequiredPositionalParameter("THING", "The thing.")
                val optionalValue: String? by OptionalPositionalParameter("THING?", "The other thing.")

                override fun createCommand(kodein: Kodein): Command = TestCommand(requiredValue, optionalValue)
            }

            rootParser.addCommandDefinition(commandDefinition)

            describe("parsing a list of arguments with just that command's name") {
                val result: CommandLineParsingResult = rootParser.parse(listOf("do-stuff"))

                it("indicates that parsing failed") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Failed("Command 'do-stuff' requires at least 1 parameter. The parameter 'THING' was not supplied.")))
                }
            }

            describe("parsing a list of arguments with the command's name and a value for the required parameter") {
                val result: CommandLineParsingResult = rootParser.parse(listOf("do-stuff", "some value"))

                it("indicates that parsing succeeded") {
                    assert.that(result, isA<Succeeded>())
                }

                it("returns a command instance ready for use") {
                    assert.that((result as Succeeded).command, equalTo<Command>(TestCommand("some value", null)))
                }
            }

            describe("parsing a list of arguments with the command's name and a value for both parameters") {
                val result: CommandLineParsingResult = rootParser.parse(listOf("do-stuff", "some value", "other value"))

                it("indicates that parsing succeeded") {
                    assert.that(result, isA<Succeeded>())
                }

                it("returns a command instance ready for use") {
                    assert.that((result as Succeeded).command, equalTo<Command>(TestCommand("some value", "other value")))
                }
            }

            describe("parsing a list of arguments with the command's name, a value for both parameters and an extra value") {
                val result: CommandLineParsingResult = rootParser.parse(listOf("do-stuff", "some value", "other value", "some extra value"))

                it("indicates that parsing failed") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Failed("Command 'do-stuff' takes at most 2 parameters. ('some extra value' is the first extra parameter.)")))
                }
            }
        }

        given("a parser with a single command that takes a required parameter and two optional parameters") {
            val injections = Kodein { }
            val rootParser = CommandLineParser(injections)

            data class TestCommand(val requiredValue: String, val firstOptionalValue: String?, val secondOptionalValue: String?) : Command {
                override fun run(): Int = throw NotImplementedError()
            }

            val commandDefinition = object : CommandDefinition("do-stuff", "Do the thing.") {
                val requiredValue: String by RequiredPositionalParameter("THING", "The thing.")
                val firstOptionalValue: String? by OptionalPositionalParameter("FIRST OTHER THING", "The first other thing.")
                val secondOptionalValue: String? by OptionalPositionalParameter("SECOND OTHER THING", "The second other thing.")

                override fun createCommand(kodein: Kodein): Command = TestCommand(requiredValue, firstOptionalValue, secondOptionalValue)
            }

            rootParser.addCommandDefinition(commandDefinition)

            describe("parsing a list of arguments with just that command's name") {
                val result: CommandLineParsingResult = rootParser.parse(listOf("do-stuff"))

                it("indicates that parsing failed") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Failed("Command 'do-stuff' requires at least 1 parameter. The parameter 'THING' was not supplied.")))
                }
            }

            describe("parsing a list of arguments with the command's name and a value for the required parameter") {
                val result: CommandLineParsingResult = rootParser.parse(listOf("do-stuff", "some value"))

                it("indicates that parsing succeeded") {
                    assert.that(result, isA<Succeeded>())
                }

                it("returns a command instance ready for use") {
                    assert.that((result as Succeeded).command, equalTo<Command>(TestCommand("some value", null, null)))
                }
            }

            describe("parsing a list of arguments with the command's name and a value for the first two parameters") {
                val result: CommandLineParsingResult = rootParser.parse(listOf("do-stuff", "some value", "other value"))

                it("indicates that parsing succeeded") {
                    assert.that(result, isA<Succeeded>())
                }

                it("returns a command instance ready for use") {
                    assert.that((result as Succeeded).command, equalTo<Command>(TestCommand("some value", "other value", null)))
                }
            }

            describe("parsing a list of arguments with the command's name and a value for all parameters") {
                val result: CommandLineParsingResult = rootParser.parse(listOf("do-stuff", "some value", "other value", "another value"))

                it("indicates that parsing succeeded") {
                    assert.that(result, isA<Succeeded>())
                }

                it("returns a command instance ready for use") {
                    assert.that((result as Succeeded).command, equalTo<Command>(TestCommand("some value", "other value", "another value")))
                }
            }

            describe("parsing a list of arguments with the command's name, a value for all three parameters and an extra value") {
                val result: CommandLineParsingResult = rootParser.parse(listOf("do-stuff", "some value", "other value", "second other value", "some extra value"))

                it("indicates that parsing failed") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Failed("Command 'do-stuff' takes at most 3 parameters. ('some extra value' is the first extra parameter.)")))
                }
            }
        }

        given("a parser with a single command that takes two required parameters") {
            val injections = Kodein { }
            val rootParser = CommandLineParser(injections)

            data class TestCommand(val firstValue: String, val secondValue: String) : Command {
                override fun run(): Int = throw NotImplementedError()
            }

            val commandDefinition = object : CommandDefinition("do-stuff", "Do the thing.") {
                val thing1: String by RequiredPositionalParameter("THING1", "The first thing.")
                val thing2: String by RequiredPositionalParameter("THING2", "The second thing.")

                override fun createCommand(kodein: Kodein): Command = TestCommand(thing1, thing2)
            }

            rootParser.addCommandDefinition(commandDefinition)

            describe("parsing a list of arguments with just that command's name") {
                val result: CommandLineParsingResult = rootParser.parse(listOf("do-stuff"))

                it("indicates that parsing failed") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Failed("Command 'do-stuff' requires at least 2 parameters. The parameter 'THING1' was not supplied.")))
                }
            }

            describe("parsing a list of arguments with the command's name and a value for only the first parameter") {
                val result: CommandLineParsingResult = rootParser.parse(listOf("do-stuff", "some value"))

                it("indicates that parsing failed") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Failed("Command 'do-stuff' requires at least 2 parameters. The parameter 'THING2' was not supplied.")))
                }
            }

            describe("parsing a list of arguments with the command's name and values for the parameters") {
                val result: CommandLineParsingResult = rootParser.parse(listOf("do-stuff", "some value", "some other value"))

                it("indicates that parsing succeeded") {
                    assert.that(result, isA<Succeeded>())
                }

                it("returns a command instance ready for use") {
                    assert.that((result as Succeeded).command, equalTo<Command>(TestCommand("some value", "some other value")))
                }
            }

            describe("parsing a list of arguments with the command's name, a value for the parameters and an extra parameter") {
                val result: CommandLineParsingResult = rootParser.parse(listOf("do-stuff", "some value", "some other value", "some extra value"))

                it("indicates that parsing failed") {
                    assert.that(result, equalTo<CommandLineParsingResult>(Failed("Command 'do-stuff' takes at most 2 parameters. ('some extra value' is the first extra parameter.)")))
                }
            }
        }
    }
})
