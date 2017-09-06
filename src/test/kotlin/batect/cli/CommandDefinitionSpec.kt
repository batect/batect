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

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import batect.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object CommandDefinitionSpec : Spek({
    describe("a command definition") {
        describe("creation") {
            fun createCommandDefinition(name: String, description: String) = object : CommandDefinition(name, description) {
                override fun createCommand(kodein: Kodein): Command = throw NotImplementedError()
            }

            on("attempting to create a command definition with a name and description") {
                it("does not throw an exception") {
                    assertThat({ createCommandDefinition("do-stuff", "Do the thing.") }, !throws<Throwable>())
                }
            }

            on("attempting to create a command definition with an empty name") {
                it("throws an exception") {
                    assertThat({ createCommandDefinition("", "Do the thing.") }, throws<IllegalArgumentException>(withMessage("Command name must not be empty.")))
                }
            }

            on("attempting to create a command definition with an empty description") {
                it("throws an exception") {
                    assertThat({ createCommandDefinition("do-stuff", "") }, throws<IllegalArgumentException>(withMessage("Command description must not be empty.")))
                }
            }
        }

        describe("parsing arguments") {
            given("a command definition with no parameters defined") {
                val emptyKodein = Kodein {}
                val command = NullCommand()
                val commandDefinition = object : CommandDefinition("test-command", "Do the thing.") {
                    override fun createCommand(kodein: Kodein): Command = command
                }

                on("parsing an empty list of arguments") {
                    val result = commandDefinition.parse(emptyList(), emptyKodein)

                    it("indicates that parsing succeeded and returns a command instance ready for use") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Succeeded(command)))
                    }
                }

                on("parsing a non-empty list of arguments") {
                    val result = commandDefinition.parse(listOf("some-arg"), emptyKodein)

                    it("indicates that parsing failed") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Failed("Command 'test-command' does not take any parameters. ('some-arg' is the first extra parameter.)")))
                    }
                }
            }

            given("a command definition with a single required positional parameter") {
                val emptyKodein = Kodein {}

                data class TestCommand(val value: String) : Command {
                    override fun run(): Int = throw NotImplementedError()
                }

                val commandDefinition = object : CommandDefinition("do-stuff", "Do the thing.") {
                    val thing: String by RequiredPositionalParameter("THING", "The thing.")

                    override fun createCommand(kodein: Kodein): Command = TestCommand(thing)
                }

                describe("parsing an empty list of arguments") {
                    val result = commandDefinition.parse(emptyList(), emptyKodein)

                    it("indicates that parsing failed") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Failed("Command 'do-stuff' requires at least 1 parameter. The parameter 'THING' was not supplied.")))
                    }
                }

                describe("parsing a list of arguments with a value for the required parameter") {
                    val result = commandDefinition.parse(listOf("some value"), emptyKodein)

                    it("indicates that parsing succeeded and returns a command instance ready for use") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Succeeded(TestCommand("some value"))))
                    }
                }

                describe("parsing a list of arguments with a value for the required parameter and an extra parameter") {
                    val result = commandDefinition.parse(listOf("some value", "some extra value"), emptyKodein)

                    it("indicates that parsing failed") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Failed("Command 'do-stuff' takes at most 1 parameter. ('some extra value' is the first extra parameter.)")))
                    }
                }
            }

            given("a command definition with a single optional positional parameter") {
                val emptyKodein = Kodein {}

                data class TestCommand(val value: String?) : Command {
                    override fun run(): Int = throw NotImplementedError()
                }

                val commandDefinition = object : CommandDefinition("do-stuff", "Do the thing.") {
                    val thing: String? by OptionalPositionalParameter("THING", "The thing.")

                    override fun createCommand(kodein: Kodein): Command = TestCommand(thing)
                }

                describe("parsing an empty list of arguments") {
                    val result = commandDefinition.parse(emptyList(), emptyKodein)

                    it("indicates that parsing succeeded and returns a command instance ready for use") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Succeeded(TestCommand(null))))
                    }
                }

                describe("parsing a list of arguments with a value for the parameter") {
                    val result = commandDefinition.parse(listOf("some value"), emptyKodein)

                    it("indicates that parsing succeeded and returns a command instance ready for use") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Succeeded(TestCommand("some value"))))
                    }
                }

                describe("parsing a list of arguments with a value for the optional parameter and an extra parameter") {
                    val result = commandDefinition.parse(listOf("some value", "some extra value"), emptyKodein)

                    it("indicates that parsing failed") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Failed("Command 'do-stuff' takes at most 1 parameter. ('some extra value' is the first extra parameter.)")))
                    }
                }
            }

            given("a command definition with a required parameter and an optional parameter") {
                val emptyKodein = Kodein {}

                data class TestCommand(val requiredValue: String, val optionalValue: String?) : Command {
                    override fun run(): Int = throw NotImplementedError()
                }

                val commandDefinition = object : CommandDefinition("do-stuff", "Do the thing.") {
                    val requiredValue: String by RequiredPositionalParameter("THING", "The thing.")
                    val optionalValue: String? by OptionalPositionalParameter("OTHER-THING", "The other thing.")

                    override fun createCommand(kodein: Kodein): Command = TestCommand(requiredValue, optionalValue)
                }

                describe("parsing an empty list of arguments") {
                    val result = commandDefinition.parse(emptyList(), emptyKodein)

                    it("indicates that parsing failed") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Failed("Command 'do-stuff' requires at least 1 parameter. The parameter 'THING' was not supplied.")))
                    }
                }

                describe("parsing a list of arguments with a value for the required parameter") {
                    val result = commandDefinition.parse(listOf("some value"), emptyKodein)

                    it("indicates that parsing succeeded and returns a command instance ready for use") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Succeeded(TestCommand("some value", null))))
                    }
                }

                describe("parsing a list of arguments with a value for both the required and optional parameters") {
                    val result = commandDefinition.parse(listOf("some value", "some other value"), emptyKodein)

                    it("indicates that parsing succeeded and returns a command instance ready for use") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Succeeded(TestCommand("some value", "some other value"))))
                    }
                }

                describe("parsing a list of arguments with a value for both parameters and an extra argument") {
                    val result = commandDefinition.parse(listOf("some value", "some other value", "some extra value"), emptyKodein)

                    it("indicates that parsing failed") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Failed("Command 'do-stuff' takes at most 2 parameters. ('some extra value' is the first extra parameter.)")))
                    }
                }
            }

            given("a command definition with a required parameter and two optional parameters") {
                val emptyKodein = Kodein {}

                data class TestCommand(val requiredValue: String, val firstOptionalValue: String?, val secondOptionalValue: String?) : Command {
                    override fun run(): Int = throw NotImplementedError()
                }

                val commandDefinition = object : CommandDefinition("do-stuff", "Do the thing.") {
                    val requiredValue: String by RequiredPositionalParameter("THING", "The thing.")
                    val firstOptionalValue: String? by OptionalPositionalParameter("FIRST OTHER THING", "The first other thing.")
                    val secondOptionalValue: String? by OptionalPositionalParameter("SECOND OTHER THING", "The second other thing.")

                    override fun createCommand(kodein: Kodein): Command = TestCommand(requiredValue, firstOptionalValue, secondOptionalValue)
                }

                describe("parsing an empty list of arguments") {
                    val result = commandDefinition.parse(emptyList(), emptyKodein)

                    it("indicates that parsing failed") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Failed("Command 'do-stuff' requires at least 1 parameter. The parameter 'THING' was not supplied.")))
                    }
                }

                describe("parsing a list of arguments with a value for the required parameter") {
                    val result = commandDefinition.parse(listOf("some value"), emptyKodein)

                    it("indicates that parsing succeeded and returns a command instance ready for use") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Succeeded(TestCommand("some value", null, null))))
                    }
                }

                describe("parsing a list of arguments with values for the first two parameters") {
                    val result = commandDefinition.parse(listOf("some value", "first optional value"), emptyKodein)

                    it("indicates that parsing succeeded and returns a command instance ready for use") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Succeeded(TestCommand("some value", "first optional value", null))))
                    }
                }

                describe("parsing a list of arguments with values for all parameters") {
                    val result = commandDefinition.parse(listOf("some value", "first optional value", "second optional value"), emptyKodein)

                    it("indicates that parsing succeeded and returns a command instance ready for use") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Succeeded(TestCommand("some value", "first optional value", "second optional value"))))
                    }
                }

                describe("parsing a list of arguments with values for all parameters and an extra argument") {
                    val result = commandDefinition.parse(listOf("some value", "first value", "second value", "some extra value"), emptyKodein)

                    it("indicates that parsing failed") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Failed("Command 'do-stuff' takes at most 3 parameters. ('some extra value' is the first extra parameter.)")))
                    }
                }
            }

            given("a command definition with two required parameters") {
                val emptyKodein = Kodein {}

                data class TestCommand(val firstValue: String, val secondValue: String) : Command {
                    override fun run(): Int = throw NotImplementedError()
                }

                val commandDefinition = object : CommandDefinition("do-stuff", "Do the thing.") {
                    val firstValue: String by RequiredPositionalParameter("FIRST-THING", "The first thing.")
                    val secondValue: String by RequiredPositionalParameter("SECOND-THING", "The second thing.")

                    override fun createCommand(kodein: Kodein): Command = TestCommand(firstValue, secondValue)
                }

                describe("parsing an empty list of arguments") {
                    val result = commandDefinition.parse(emptyList(), emptyKodein)

                    it("indicates that parsing failed") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Failed("Command 'do-stuff' requires at least 2 parameters. The parameter 'FIRST-THING' was not supplied.")))
                    }
                }

                describe("parsing a list of arguments with a value for only the first parameter") {
                    val result = commandDefinition.parse(listOf("some value"), emptyKodein)

                    it("indicates that parsing failed") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Failed("Command 'do-stuff' requires at least 2 parameters. The parameter 'SECOND-THING' was not supplied.")))
                    }
                }

                describe("parsing a list of arguments with values for both parameters") {
                    val result = commandDefinition.parse(listOf("some value", "some other value"), emptyKodein)

                    it("indicates that parsing succeeded and returns a command instance ready for use") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Succeeded(TestCommand("some value", "some other value"))))
                    }
                }

                describe("parsing a list of arguments with values for both parameters and an extra argument") {
                    val result = commandDefinition.parse(listOf("some value", "some other value", "some extra value"), emptyKodein)

                    it("indicates that parsing failed") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Failed("Command 'do-stuff' takes at most 2 parameters. ('some extra value' is the first extra parameter.)")))
                    }
                }
            }

            given("a command definition and a non-empty Kodein container") {
                val kodeinContainer = Kodein {
                    bind<String>("the-value") with instance("This is the value")
                }

                data class TestCommand(val value: String) : Command {
                    override fun run(): Int = throw NotImplementedError()
                }

                val commandDefinition = object : CommandDefinition("do-stuff", "Do the thing.") {
                    override fun createCommand(kodein: Kodein): Command = TestCommand(kodein.instance("the-value"))
                }

                on("parsing a list of valid arguments for the command") {
                    val result = commandDefinition.parse(emptyList(), kodeinContainer)

                    it("indicates that parsing succeeded and returns a command instance populated with the value from the container") {
                        assertThat(result, equalTo<CommandLineParsingResult>(Succeeded(TestCommand("This is the value"))))
                    }
                }
            }
        }
    }
})
