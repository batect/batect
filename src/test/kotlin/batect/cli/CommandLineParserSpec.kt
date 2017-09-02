package batect.cli

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isIn
import com.natpryce.hamkrest.throws
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argThat
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import batect.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object CommandLineParserSpec : Spek({
    describe("a command line parser") {
        describe("parsing arguments") {
            val optionParser = mock<OptionParser>()

            val injections = Kodein {
                bind<String>("the-string-from-original-container") with instance("The original string")
            }

            val parser = object : CommandLineParser(injections, optionParser) {
                override fun createBindings(): Kodein.Module {
                    return Kodein.Module {
                        bind<String>("the-string-from-created-container") with instance("The additional string")
                    }
                }
            }

            val commandDefinition = mock<CommandDefinition> {
                on { commandName } doReturn "do-stuff"
                on { aliases } doReturn setOf("ds")
            }

            parser.addCommandDefinition(commandDefinition)

            beforeEachTest {
                reset(optionParser)
                reset(commandDefinition)
            }

            describe("when the option parser returns an error") {
                beforeEachTest {
                    whenever(optionParser.parseOptions(any())).thenReturn(InvalidOptions("Something was invalid"))
                }

                on("parsing a list of arguments") {
                    it("indicates that parsing failed") {
                        assertThat(parser.parse(listOf("abc", "123")),
                                equalTo<CommandLineParsingResult>(Failed("Something was invalid")))
                    }
                }
            }

            describe("when the option parser indicates that option parsing succeeded") {
                beforeEachTest {
                    whenever(optionParser.parseOptions(any())).thenReturn(ReadOptions(2))
                }

                on("parsing a list of arguments where the option parser consumes all the arguments") {
                    val result = parser.parse(listOf("--some-option", "--some-other-option"))

                    it("indicates that parsing failed because no command was provided") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Failed("No command specified. Run 'decompose help' for a list of valid commands.")))
                    }
                }

                on("parsing a list of arguments where the command given does not exist") {
                    val result = parser.parse(listOf("--some-option", "--some-other-option", "some-non-existent-command"))

                    it("indicates that parsing failed") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Failed("Invalid command 'some-non-existent-command'. Run 'decompose help' for a list of valid commands.")))
                    }
                }

                on("parsing a list of arguments where the option given does not exist") {
                    val result = parser.parse(listOf("--some-option", "--some-other-option", "--some-unknown-option", "do-stuff"))

                    it("indicates that parsing failed") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Failed("Invalid option '--some-unknown-option'. Run 'decompose help' for a list of valid options.")))
                    }
                }

                describe("parsing a list of arguments where the command given does exist") {
                    describe("and the command parses its arguments successfully") {
                        val command = NullCommand()

                        beforeEachTest {
                            whenever(commandDefinition.parse(any(), any())).thenReturn(Succeeded(command))
                        }

                        on("no command-specific options being provided") {
                            val result = parser.parse(listOf("--some-option", "--some-other-option", "do-stuff"))

                            it("indicates that parsing succeeded") {
                                assertThat(result, equalTo<CommandLineParsingResult>(Succeeded(command)))
                            }

                            it("passes an empty list of arguments to the command") {
                                verify(commandDefinition).parse(argThat { count() == 0 }, any())
                            }

                            it("passes a Kodein container containing the original injections to the command") {
                                verify(commandDefinition).parse(any(), argThat { instance<String>("the-string-from-original-container") == "The original string" })
                            }

                            it("passes a Kodein container containing the injections created by the parser to the command") {
                                verify(commandDefinition).parse(any(), argThat { instance<String>("the-string-from-created-container") == "The additional string" })
                            }
                        }

                        on("some command-specific options being provided") {
                            val result = parser.parse(listOf("--some-option", "--some-other-option", "do-stuff", "some-command-parameter"))

                            it("indicates that parsing succeeded") {
                                assertThat(result, equalTo<CommandLineParsingResult>(Succeeded(command)))
                            }

                            it("passes the command-specific arguments to the command") {
                                verify(commandDefinition).parse(argThat { this == listOf("some-command-parameter") }, any())
                            }

                            it("passes a Kodein container containing the original injections to the command") {
                                verify(commandDefinition).parse(any(), argThat { instance<String>("the-string-from-original-container") == "The original string" })
                            }

                            it("passes a Kodein container containing the injections created by the parser to the command") {
                                verify(commandDefinition).parse(any(), argThat { instance<String>("the-string-from-created-container") == "The additional string" })
                            }
                        }
                    }

                    describe("and the command does not parse its arguments successfully") {
                        beforeEachTest {
                            whenever(commandDefinition.parse(any(), any())).thenReturn(Failed("Something went wrong"))
                        }

                        on("parsing a list of arguments") {
                            val result = parser.parse(listOf("--some-option", "--some-other-option", "do-stuff"))

                            it("indicates that parsing failed") {
                                assertThat(result, equalTo<CommandLineParsingResult>(Failed("Something went wrong")))
                            }
                        }
                    }

                    describe("and the command is referred to by an alias") {
                        val command = NullCommand()

                        beforeEachTest {
                            whenever(commandDefinition.parse(any(), any())).thenReturn(Succeeded(command))
                        }

                        on("parsing a list of arguments") {
                            val result = parser.parse(listOf("--some-option", "--some-other-option", "ds"))

                            it("indicates that parsing succeeded") {
                                assertThat(result, equalTo<CommandLineParsingResult>(Succeeded(command)))
                            }
                        }
                    }
                }
            }
        }

        describe("adding a command definition to the list of commands") {
            val injections = Kodein { }
            val parser = CommandLineParser(injections)

            val commandDefinition = object : CommandDefinition("do-stuff", "Do the thing.", aliases = setOf("do-stuff-alias")) {
                override fun createCommand(kodein: Kodein): Command = NullCommand()
            }

            parser.addCommandDefinition(commandDefinition)

            on("getting a list of all registered command definitions") {
                val commands = parser.getAllCommandDefinitions()

                it("includes the newly registered command") {
                    assertThat(commandDefinition, isIn(commands))
                }
            }

            on("getting the command by its name") {
                val commandFound = parser.getCommandDefinitionByName("do-stuff")

                it("returns the newly registered command") {
                    assertThat(commandFound, equalTo<CommandDefinition?>(commandDefinition))
                }
            }

            on("getting the command by its alias") {
                val commandFound = parser.getCommandDefinitionByName("do-stuff-alias")

                it("returns the newly registered command") {
                    assertThat(commandFound, equalTo<CommandDefinition?>(commandDefinition))
                }
            }

            on("attempting to add a new command with the same name") {
                val newCommand = object : CommandDefinition("do-stuff", "The other do-stuff.") {
                    override fun createCommand(kodein: Kodein): Command = throw NotImplementedError()
                }

                it("throws an exception") {
                    assertThat({ parser.addCommandDefinition(newCommand) }, throws(withMessage("A command with the name or alias 'do-stuff' is already registered.")))
                }
            }

            on("attempting to add a new command with the same alias") {
                val newCommand = object : CommandDefinition("do-something-else", "The other do-stuff.", aliases = setOf("do-stuff-alias")) {
                    override fun createCommand(kodein: Kodein): Command = throw NotImplementedError()
                }

                it("throws an exception") {
                    assertThat({ parser.addCommandDefinition(newCommand) }, throws(withMessage("A command with the name or alias 'do-stuff-alias' is already registered.")))
                }
            }

            on("attempting to add a new command with an alias with the same name as the existing command") {
                val newCommand = object : CommandDefinition("do-other-stuff", "The other do-stuff.", aliases = setOf("do-stuff")) {
                    override fun createCommand(kodein: Kodein): Command = throw NotImplementedError()
                }

                it("throws an exception") {
                    assertThat({ parser.addCommandDefinition(newCommand) }, throws(withMessage("A command with the name or alias 'do-stuff' is already registered.")))
                }
            }

            on("attempting to add a new command with a name that is the same as an existing command's alias") {
                val newCommand = object : CommandDefinition("do-stuff-alias", "The other do-stuff.") {
                    override fun createCommand(kodein: Kodein): Command = throw NotImplementedError()
                }

                it("throws an exception") {
                    assertThat({ parser.addCommandDefinition(newCommand) }, throws(withMessage("A command with the name or alias 'do-stuff-alias' is already registered.")))
                }
            }
        }
    }
})
