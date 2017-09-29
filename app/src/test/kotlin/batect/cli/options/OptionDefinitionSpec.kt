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

package batect.cli.options

import batect.testutils.CreateForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object OptionDefinitionSpec : Spek({
    describe("a option definition") {
        fun createOption(longName: String, description: String, shortName: Char? = null): OptionDefinition {
            return object : OptionDefinition(longName, description, false, shortName) {
                override fun parseValue(args: Iterable<String>): OptionParsingResult = throw NotImplementedError()
            }
        }

        describe("creation") {
            on("attempting to create an value option with a valid name and description") {
                it("does not throw an exception") {
                    assertThat({ createOption("value", "The value.") }, !throws<Throwable>())
                }
            }

            on("attempting to create an value option with a valid name, description and short name") {
                it("does not throw an exception") {
                    assertThat({ createOption("value", "The value.", 'v') }, !throws<Throwable>())
                }
            }

            on("attempting to create an value option with a name with dashes") {
                it("does not throw an exception") {
                    assertThat({ createOption("some-value", "The value.") }, !throws<Throwable>())
                }
            }

            on("attempting to create an value option with an empty name") {
                it("throws an exception") {
                    assertThat({ createOption("", "The value.") }, throws<IllegalArgumentException>(withMessage("Option long name must not be empty.")))
                }
            }

            on("attempting to create an value option with an empty description") {
                it("throws an exception") {
                    assertThat({ createOption("value", "") }, throws<IllegalArgumentException>(withMessage("Option description must not be empty.")))
                }
            }

            on("attempting to create an value option with a name that starts with a dash") {
                it("throws an exception") {
                    assertThat({ createOption("-value", "The value.") }, throws<IllegalArgumentException>(withMessage("Option long name must not start with a dash.")))
                }
            }

            on("attempting to create an value option with a name that is only one character long") {
                it("throws an exception") {
                    assertThat({ createOption("v", "The value.") }, throws<IllegalArgumentException>(withMessage("Option long name must be at least two characters long.")))
                }
            }

            on("attempting to create an value option with a short name that is a dash") {
                it("throws an exception") {
                    assertThat({ createOption("value", "The value.", '-') }, throws<IllegalArgumentException>(withMessage("Option short name must be alphanumeric.")))
                }
            }

            on("attempting to create an value option with a short name that is a space") {
                it("throws an exception") {
                    assertThat({ createOption("value", "The value.", ' ') }, throws<IllegalArgumentException>(withMessage("Option short name must be alphanumeric.")))
                }
            }
        }

        describe("parsing") {
            class TestOptionDefinition(name: String,
                                       description: String,
                                       shortName: Char? = null) : OptionDefinition(name, description, false, shortName) {
                override fun parseValue(args: Iterable<String>): OptionParsingResult = OptionParsingResult.ReadOption(1234)
            }

            given("an option with short and long names") {
                val option by CreateForEachTest(this) { TestOptionDefinition("value", "The value", 'v') }

                on("parsing an empty list of arguments") {
                    it("throws an exception") {
                        assertThat({ option.parse(emptyList()) }, throws<IllegalArgumentException>(withMessage("List of arguments cannot be empty.")))
                    }
                }

                on("parsing a list of arguments where the option is not specified") {
                    it("throws an exception") {
                        assertThat({ option.parse(listOf("do-stuff")) }, throws<IllegalArgumentException>(withMessage("Next argument in list of arguments is not for this option.")))
                    }
                }

                setOf("--value", "-v").forEach { format ->
                    on("parsing a list of arguments that has the correct option name in the format $format") {
                        it("returns the parsing result from the concrete implementation") {
                            assertThat(option.parse(listOf(format, "something")), equalTo<OptionParsingResult>(OptionParsingResult.ReadOption(1234)))
                        }
                    }
                }

                allPairs(
                    listOf("--value=thing"),
                    listOf("--value", "thing"),
                    listOf("--value"),
                    listOf("-v=thing"),
                    listOf("-v", "thing"),
                    listOf("-v")
                ).forEach { (first, second) ->
                    on("parsing a list of arguments where the option is valid but given twice in the form ${first + second}") {
                        option.parse(first + second + "do-stuff")
                        val result = option.parse(second + "do-stuff")

                        it("indicates that parsing failed") {
                            assertThat(result, equalTo<OptionParsingResult>(OptionParsingResult.InvalidOption("Option '--value' (or '-v') cannot be specified multiple times.")))
                        }
                    }
                }
            }

            given("an option without a short name") {
                val option = TestOptionDefinition("value", "The value")

                given("and the option has already parsed a valid value") {
                    beforeEachTest {
                        option.parse(listOf("--value=thing", "--value=other-thing", "do-stuff"))
                    }

                    on("parsing another list of arguments where the option is specified again") {
                        val result = option.parse(listOf("--value=other-thing", "do-stuff"))

                        it("indicates that parsing failed") {
                            assertThat(result, equalTo<OptionParsingResult>(OptionParsingResult.InvalidOption("Option '--value' cannot be specified multiple times.")))
                        }
                    }
                }
            }
        }

        describe("option formatting") {
            on("an option without a short name") {
                val option = createOption("value", "Some description", null)

                it("returns the correctly formatted long option") {
                    assertThat(option.longOption, equalTo("--value"))
                }

                it("returns a null short option") {
                    assertThat(option.shortOption, absent())
                }
            }

            on("an option with a short name") {
                val option = createOption("value", "Some description", 'v')

                it("returns the correctly formatted long option") {
                    assertThat(option.longOption, equalTo("--value"))
                }

                it("returns a null short option") {
                    assertThat(option.shortOption, equalTo("-v"))
                }
            }
        }
    }
})

fun <T> allPairs(vararg possibilities: T): Iterable<Pair<T, T>>
    = possibilities.flatMap { first -> possibilities.map { second -> first to second } }
