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

package batect.cli.commands

import batect.cli.CommandLineParser
import batect.cli.options.OptionDefinition
import batect.cli.options.ValueApplicationResult
import batect.cli.testutils.NullCommand
import com.github.salomonbrys.kodein.Kodein
import com.natpryce.hamkrest.assertion.assertThat
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

        fun createOption(longName: String, description: String, shortName: Char? = null): OptionDefinition =
                object : OptionDefinition(longName, description, shortName) {
                    override fun applyValue(newValue: String): ValueApplicationResult = throw NotImplementedError()
                    override val descriptionForHelp: String
                        get() = description + " (extra help info)"
                }

        given("no command name to show help for") {
            given("and the root parser has no common options") {
                val output = ByteArrayOutputStream()
                val outputStream = PrintStream(output)
                val parser = mock<CommandLineParser> {
                    on { applicationName } doReturn "the-cool-app"
                    on { getAllCommandDefinitions() } doReturn setOf(firstCommandDefinition, secondCommandDefinition)
                    on { getCommonOptions() } doReturn emptySet()
                }

                val command = HelpCommand(null, parser, outputStream)
                val exitCode = command.run()

                it("prints help information") {
                    assertThat(output.toString(), equalTo("""
                        |Usage: the-cool-app COMMAND [COMMAND OPTIONS]
                        |
                        |Commands:
                        |  do-other-stuff    Do the other thing.
                        |  do-stuff          Do the thing.
                        |
                        |For help on the options available for a command, run 'the-cool-app help <command>'.
                        |
                        |""".trimMargin()))
                }

                it("returns a non-zero exit code") {
                    assertThat(exitCode, !equalTo(0))
                }
            }

            given("and the root parser has some common options") {
                val output = ByteArrayOutputStream()
                val outputStream = PrintStream(output)

                val parser = mock<CommandLineParser> {
                    on { applicationName } doReturn "the-cool-app"
                    on { getAllCommandDefinitions() } doReturn setOf(firstCommandDefinition, secondCommandDefinition)
                    on { getCommonOptions() } doReturn setOf<OptionDefinition>(
                            createOption("awesomeness-level", "Level of awesomeness to use."),
                            createOption("booster-level", "Level of boosters to use."),
                            createOption("file", "File name to use.", 'f'),
                            createOption("sensible-default", "Something you can override if you want.")
                    )
                }

                val command = HelpCommand(null, parser, outputStream)
                val exitCode = command.run()

                it("prints help information") {
                    assertThat(output.toString(), equalTo("""
                        |Usage: the-cool-app [COMMON OPTIONS] COMMAND [COMMAND OPTIONS]
                        |
                        |Commands:
                        |  do-other-stuff                   Do the other thing.
                        |  do-stuff                         Do the thing.
                        |
                        |Common options:
                        |      --awesomeness-level=value    Level of awesomeness to use. (extra help info)
                        |      --booster-level=value        Level of boosters to use. (extra help info)
                        |  -f, --file=value                 File name to use. (extra help info)
                        |      --sensible-default=value     Something you can override if you want. (extra help info)
                        |
                        |For help on the options available for a command, run 'the-cool-app help <command>'.
                        |
                        |""".trimMargin()))
                }

                it("returns a non-zero exit code") {
                    assertThat(exitCode, !equalTo(0))
                }
            }
        }

        given("a command name to show help for") {
            given("and that command name is a valid command name") {
                given("the root parser has some common options") {
                    val commonOptions = setOf(createOption("some-option", "Some common option."))

                    given("and that command has no options or positional parameters") {
                        val output = ByteArrayOutputStream()
                        val outputStream = PrintStream(output)
                        val parser = mock<CommandLineParser> {
                            on { applicationName } doReturn "the-cool-app"
                            on { getCommandDefinitionByName("do-stuff") } doReturn firstCommandDefinition
                            on { getCommonOptions() } doReturn commonOptions
                        }

                        val command = HelpCommand("do-stuff", parser, outputStream)
                        val exitCode = command.run()

                        it("prints help information") {
                            assertThat(output.toString(), equalTo("""
                            |Usage: the-cool-app [COMMON OPTIONS] do-stuff
                            |
                            |Do the thing.
                            |
                            |This command does not take any options.
                            |
                            |For help on the common options available for all commands, run 'the-cool-app help'.
                            |
                            |""".trimMargin()))
                        }

                        it("returns a non-zero exit code") {
                            assertThat(exitCode, !equalTo(0))
                        }
                    }

                    given("and that command has a single optional positional parameter") {
                        val output = ByteArrayOutputStream()
                        val outputStream = PrintStream(output)

                        val parser = mock<CommandLineParser> {
                            on { applicationName } doReturn "the-cool-app"
                            on { getCommonOptions() } doReturn commonOptions
                            on { getCommandDefinitionByName("do-stuff") } doReturn object : CommandDefinition("do-stuff", "Do the thing.") {
                                val thingToDo: String? by OptionalPositionalParameter("THING", "Thing to do.")

                                override fun createCommand(kodein: Kodein): Command = NullCommand()
                            }
                        }

                        val command = HelpCommand("do-stuff", parser, outputStream)
                        val exitCode = command.run()

                        it("prints help information") {
                            assertThat(output.toString(), equalTo("""
                            |Usage: the-cool-app [COMMON OPTIONS] do-stuff [THING]
                            |
                            |Do the thing.
                            |
                            |Parameters:
                            |  THING    (optional) Thing to do.
                            |
                            |For help on the common options available for all commands, run 'the-cool-app help'.
                            |
                            |""".trimMargin()))
                        }

                        it("returns a non-zero exit code") {
                            assertThat(exitCode, !equalTo(0))
                        }
                    }

                    given("and that command has a single required positional parameter") {
                        val output = ByteArrayOutputStream()
                        val outputStream = PrintStream(output)

                        val parser = mock<CommandLineParser> {
                            on { applicationName } doReturn "the-cool-app"
                            on { getCommonOptions() } doReturn commonOptions
                            on { getCommandDefinitionByName("do-stuff") } doReturn object : CommandDefinition("do-stuff", "Do the thing.") {
                                val thingToDo: String by RequiredPositionalParameter("THING", "Thing to do.")

                                override fun createCommand(kodein: Kodein): Command = NullCommand()
                            }
                        }

                        val command = HelpCommand("do-stuff", parser, outputStream)
                        val exitCode = command.run()

                        it("prints help information") {
                            assertThat(output.toString(), equalTo("""
                            |Usage: the-cool-app [COMMON OPTIONS] do-stuff THING
                            |
                            |Do the thing.
                            |
                            |Parameters:
                            |  THING    Thing to do.
                            |
                            |For help on the common options available for all commands, run 'the-cool-app help'.
                            |
                            |""".trimMargin()))
                        }

                        it("returns a non-zero exit code") {
                            assertThat(exitCode, !equalTo(0))
                        }
                    }

                    given("and that command has two required positional parameters") {
                        val output = ByteArrayOutputStream()
                        val outputStream = PrintStream(output)

                        val parser = mock<CommandLineParser> {
                            on { applicationName } doReturn "the-cool-app"
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
                            assertThat(output.toString(), equalTo("""
                            |Usage: the-cool-app [COMMON OPTIONS] do-stuff THING OTHER-THING
                            |
                            |Do the thing.
                            |
                            |Parameters:
                            |  THING          Thing to do.
                            |  OTHER-THING    Other thing to do.
                            |
                            |For help on the common options available for all commands, run 'the-cool-app help'.
                            |
                            |""".trimMargin()))
                        }

                        it("returns a non-zero exit code") {
                            assertThat(exitCode, !equalTo(0))
                        }
                    }

                    given("and that command has a required and an optional parameter") {
                        val output = ByteArrayOutputStream()
                        val outputStream = PrintStream(output)

                        val parser = mock<CommandLineParser> {
                            on { applicationName } doReturn "the-cool-app"
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
                            assertThat(output.toString(), equalTo("""
                            |Usage: the-cool-app [COMMON OPTIONS] do-stuff THING [OTHER-THING]
                            |
                            |Do the thing.
                            |
                            |Parameters:
                            |  THING          Thing to do.
                            |  OTHER-THING    (optional) Other thing to do.
                            |
                            |For help on the common options available for all commands, run 'the-cool-app help'.
                            |
                            |""".trimMargin()))
                        }

                        it("returns a non-zero exit code") {
                            assertThat(exitCode, !equalTo(0))
                        }
                    }

                    given("and that command has an option") {
                        val output = ByteArrayOutputStream()
                        val outputStream = PrintStream(output)

                        val parser = mock<CommandLineParser> {
                            on { applicationName } doReturn "the-cool-app"
                            on { getCommonOptions() } doReturn commonOptions
                            on { getCommandDefinitionByName("do-stuff") } doReturn object : CommandDefinition("do-stuff", "Do the thing.") {
                                val someOption: String? by valueOption("some-option", "Some option that you can set.", 'o')

                                override fun createCommand(kodein: Kodein): Command = NullCommand()
                            }
                        }

                        val command = HelpCommand("do-stuff", parser, outputStream)
                        val exitCode = command.run()

                        it("prints help information") {
                            assertThat(output.toString(), equalTo("""
                            |Usage: the-cool-app [COMMON OPTIONS] do-stuff [OPTIONS]
                            |
                            |Do the thing.
                            |
                            |Options:
                            |  -o, --some-option=value    Some option that you can set.
                            |
                            |For help on the common options available for all commands, run 'the-cool-app help'.
                            |
                            |""".trimMargin()))
                        }

                        it("returns a non-zero exit code") {
                            assertThat(exitCode, !equalTo(0))
                        }
                    }

                    given("and that command has some options and some parameters") {
                        val output = ByteArrayOutputStream()
                        val outputStream = PrintStream(output)

                        val parser = mock<CommandLineParser> {
                            on { applicationName } doReturn "the-cool-app"
                            on { getCommonOptions() } doReturn commonOptions
                            on { getCommandDefinitionByName("do-stuff") } doReturn object : CommandDefinition("do-stuff", "Do the thing.") {
                                val someOption: String? by valueOption("some-option", "Some option that you can set.", 'o')
                                val anotherOption: String? by valueOption("another-option", "Some other option that you can set.")
                                val thingToDo: String by RequiredPositionalParameter("THING", "Thing to do.")
                                val otherThingToDo: String? by OptionalPositionalParameter("OTHER-THING", "Other thing to do.")

                                override fun createCommand(kodein: Kodein): Command = NullCommand()
                            }
                        }

                        val command = HelpCommand("do-stuff", parser, outputStream)
                        val exitCode = command.run()

                        it("prints help information") {
                            assertThat(output.toString(), equalTo("""
                            |Usage: the-cool-app [COMMON OPTIONS] do-stuff [OPTIONS] THING [OTHER-THING]
                            |
                            |Do the thing.
                            |
                            |Options:
                            |      --another-option=value    Some other option that you can set.
                            |  -o, --some-option=value       Some option that you can set.
                            |
                            |Parameters:
                            |  THING                         Thing to do.
                            |  OTHER-THING                   (optional) Other thing to do.
                            |
                            |For help on the common options available for all commands, run 'the-cool-app help'.
                            |
                            |""".trimMargin()))
                        }

                        it("returns a non-zero exit code") {
                            assertThat(exitCode, !equalTo(0))
                        }
                    }
                }

                given("the root parser has no options") {
                    val output = ByteArrayOutputStream()
                    val outputStream = PrintStream(output)
                    val parser = mock<CommandLineParser> {
                        on { applicationName } doReturn "the-cool-app"
                        on { getCommandDefinitionByName("do-stuff") } doReturn firstCommandDefinition
                    }

                    val command = HelpCommand("do-stuff", parser, outputStream)
                    val exitCode = command.run()

                    it("does not include the placeholder for common options in the header or print a message about common options") {
                        assertThat(output.toString(), equalTo("""
                            |Usage: the-cool-app do-stuff
                            |
                            |Do the thing.
                            |
                            |This command does not take any options.
                            |
                            |""".trimMargin()))
                    }

                    it("returns a non-zero exit code") {
                        assertThat(exitCode, !equalTo(0))
                    }
                }
            }

            given("and that command name is not a valid command name") {
                val output = ByteArrayOutputStream()
                val outputStream = PrintStream(output)
                val parser = mock<CommandLineParser> {
                    on { applicationName } doReturn "the-cool-app"
                }

                val command = HelpCommand("unknown-command", parser, outputStream)
                val exitCode = command.run()

                it("prints an error message") {
                    assertThat(output.toString(), equalTo("Invalid command 'unknown-command'. Run 'the-cool-app help' for a list of valid commands.\n"))
                }

                it("returns a non-zero exit code") {
                    assertThat(exitCode, !equalTo(0))
                }
            }
        }
    }
})
