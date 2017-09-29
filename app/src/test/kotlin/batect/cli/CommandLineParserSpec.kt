/*
   Copyright 2017 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.cli

import batect.cli.commands.Command
import batect.cli.commands.CommandDefinition
import batect.cli.options.OptionParser
import batect.cli.options.OptionsParsingResult
import batect.cli.testutils.NullCommand
import batect.testutils.withMessage
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

            val parser = object : CommandLineParser(injections, "the-cool-app", "For more help, visit www.help.com.", optionParser) {
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

            var initializeAfterCommonOptionsParsedCalled = false
            var originalKodeinContainerValueSeenInInitializeFunction: String? = null
            var createdKodeinContainerValueSeenInInitializeFunction: String? = null

            val initializeAfterCommonOptionsParsed = { kodein: Kodein ->
                originalKodeinContainerValueSeenInInitializeFunction = kodein.instance<String>("the-string-from-original-container")
                createdKodeinContainerValueSeenInInitializeFunction = kodein.instance<String>("the-string-from-created-container")

                initializeAfterCommonOptionsParsedCalled = true
            }

            beforeEachTest {
                reset(optionParser)
                reset(commandDefinition)

                initializeAfterCommonOptionsParsedCalled = false
                originalKodeinContainerValueSeenInInitializeFunction = null
                createdKodeinContainerValueSeenInInitializeFunction = null
            }

            describe("when the option parser returns an error") {
                beforeEachTest {
                    whenever(optionParser.parseOptions(any())).thenReturn(OptionsParsingResult.InvalidOptions("Something was invalid"))
                }

                on("parsing a list of arguments") {
                    it("indicates that parsing failed") {
                        assertThat(parser.parse(listOf("abc", "123"), initializeAfterCommonOptionsParsed),
                            equalTo<CommandLineParsingResult>(CommandLineParsingResult.Failed("Something was invalid")))
                    }

                    it("does not call the post-common options parsing initialisation function") {
                        assertThat(initializeAfterCommonOptionsParsedCalled, equalTo(false))
                    }
                }
            }

            describe("when the option parser indicates that option parsing succeeded") {
                beforeEachTest {
                    whenever(optionParser.parseOptions(any())).thenReturn(OptionsParsingResult.ReadOptions(2))
                }

                on("parsing a list of arguments where the option parser consumes all the arguments") {
                    val result = parser.parse(listOf("--some-option", "--some-other-option"), initializeAfterCommonOptionsParsed)

                    it("indicates that parsing failed because no command was provided") {
                        assertThat(result, equalTo<CommandLineParsingResult>(CommandLineParsingResult.Failed("No command specified. Run 'the-cool-app help' for a list of valid commands.")))
                    }

                    it("does not call the post-common options parsing initialisation function") {
                        assertThat(initializeAfterCommonOptionsParsedCalled, equalTo(false))
                    }
                }

                on("parsing a list of arguments where the command given does not exist") {
                    val result = parser.parse(listOf("--some-option", "--some-other-option", "some-non-existent-command"), initializeAfterCommonOptionsParsed)

                    it("indicates that parsing failed") {
                        assertThat(result, equalTo<CommandLineParsingResult>(CommandLineParsingResult.Failed("Invalid command 'some-non-existent-command'. Run 'the-cool-app help' for a list of valid commands.")))
                    }

                    it("does not call the post-common options parsing initialisation function") {
                        assertThat(initializeAfterCommonOptionsParsedCalled, equalTo(false))
                    }
                }

                on("parsing a list of arguments where the option given does not exist") {
                    val result = parser.parse(listOf("--some-option", "--some-other-option", "--some-unknown-option", "do-stuff"), initializeAfterCommonOptionsParsed)

                    it("indicates that parsing failed") {
                        assertThat(result, equalTo<CommandLineParsingResult>(CommandLineParsingResult.Failed("Invalid option '--some-unknown-option'. Run 'the-cool-app help' for a list of valid options.")))
                    }

                    it("does not call the post-common options parsing initialisation function") {
                        assertThat(initializeAfterCommonOptionsParsedCalled, equalTo(false))
                    }
                }

                describe("parsing a list of arguments where the command given does exist") {
                    describe("and the command parses its arguments successfully") {
                        val command = NullCommand()

                        beforeEachTest {
                            whenever(commandDefinition.parse(any(), any())).thenReturn(CommandLineParsingResult.Succeeded(command))
                        }

                        on("no command-specific options being provided") {
                            val result = parser.parse(listOf("--some-option", "--some-other-option", "do-stuff"), initializeAfterCommonOptionsParsed)

                            it("indicates that parsing succeeded") {
                                assertThat(result, equalTo<CommandLineParsingResult>(CommandLineParsingResult.Succeeded(command)))
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

                            it("calls the post-common options parsing initialisation function") {
                                assertThat(initializeAfterCommonOptionsParsedCalled, equalTo(true))
                            }

                            it("includes the original injections in the container passed to the initialisation function") {
                                assertThat(originalKodeinContainerValueSeenInInitializeFunction, equalTo("The original string"))
                            }

                            it("includes the created injections in the container passed to the initialisation function") {
                                assertThat(createdKodeinContainerValueSeenInInitializeFunction, equalTo("The additional string"))
                            }
                        }

                        on("some command-specific options being provided") {
                            val result = parser.parse(listOf("--some-option", "--some-other-option", "do-stuff", "some-command-parameter"), initializeAfterCommonOptionsParsed)

                            it("indicates that parsing succeeded") {
                                assertThat(result, equalTo<CommandLineParsingResult>(CommandLineParsingResult.Succeeded(command)))
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

                            it("calls the post-common options parsing initialisation function") {
                                assertThat(initializeAfterCommonOptionsParsedCalled, equalTo(true))
                            }

                            it("includes the original injections in the container passed to the initialisation function") {
                                assertThat(originalKodeinContainerValueSeenInInitializeFunction, equalTo("The original string"))
                            }

                            it("includes the created injections in the container passed to the initialisation function") {
                                assertThat(createdKodeinContainerValueSeenInInitializeFunction, equalTo("The additional string"))
                            }
                        }
                    }

                    describe("and the command does not parse its arguments successfully") {
                        beforeEachTest {
                            whenever(commandDefinition.parse(any(), any())).thenReturn(CommandLineParsingResult.Failed("Something went wrong"))
                        }

                        on("parsing a list of arguments") {
                            val result = parser.parse(listOf("--some-option", "--some-other-option", "do-stuff"), initializeAfterCommonOptionsParsed)

                            it("indicates that parsing failed") {
                                assertThat(result, equalTo<CommandLineParsingResult>(CommandLineParsingResult.Failed("Something went wrong")))
                            }

                            it("still calls the post-common options parsing initialisation function") {
                                assertThat(initializeAfterCommonOptionsParsedCalled, equalTo(true))
                            }
                        }
                    }

                    describe("and the command is referred to by an alias") {
                        val command = NullCommand()

                        beforeEachTest {
                            whenever(commandDefinition.parse(any(), any())).thenReturn(CommandLineParsingResult.Succeeded(command))
                        }

                        on("parsing a list of arguments") {
                            val result = parser.parse(listOf("--some-option", "--some-other-option", "ds"), initializeAfterCommonOptionsParsed)

                            it("indicates that parsing succeeded") {
                                assertThat(result, equalTo<CommandLineParsingResult>(CommandLineParsingResult.Succeeded(command)))
                            }

                            it("calls the post-common options parsing initialisation function") {
                                assertThat(initializeAfterCommonOptionsParsedCalled, equalTo(true))
                            }
                        }
                    }
                }
            }
        }

        describe("adding a command definition to the list of commands") {
            val injections = Kodein { }
            val parser = CommandLineParser(injections, "the-cool-app", "For more help, visit www.help.com.")

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
