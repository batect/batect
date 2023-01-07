/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.cli.options

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SingleValueMapOptionSpec : Spek({
    describe("a single value map option") {
        val group = OptionGroup("the group")

        describe("parsing") {
            given("an option with short and long names") {
                val option by createForEachTest { SingleValueMapOption(group, "option", "Some option.", 'o') }

                listOf("--option", "-o").forEach { format ->
                    on("parsing a list of arguments where the option is specified in the form '$format key=value'") {
                        val result by runForEachTest { option.parse(listOf(format, "key=value", "do-stuff")) }

                        it("indicates that parsing succeeded and that two arguments were consumed") {
                            assertThat(result, equalTo(OptionParsingResult.ReadOption(2)))
                        }

                        it("sets the option's value") {
                            assertThat(option.getValue(mock(), mock()), equalTo(mapOf("key" to "value")))
                        }

                        it("reports that the value is from the command line") {
                            assertThat(option.valueSource, equalTo(OptionValueSource.CommandLine))
                        }
                    }

                    on("parsing a list of arguments where the option is specified in the form '$format=key=value'") {
                        val result by runForEachTest { option.parse(listOf("$format=key=value", "do-stuff")) }

                        it("indicates that parsing succeeded and that one argument was consumed") {
                            assertThat(result, equalTo(OptionParsingResult.ReadOption(1)))
                        }

                        it("sets the option's value") {
                            assertThat(option.getValue(mock(), mock()), equalTo(mapOf("key" to "value")))
                        }

                        it("reports that the value is from the command line") {
                            assertThat(option.valueSource, equalTo(OptionValueSource.CommandLine))
                        }
                    }

                    listOf(
                        "a",
                        "a=",
                        "=a",
                        "=",
                    ).forEach { value ->
                        on("parsing a list of arguments where the option is given an invalid key/value pair") {
                            val result by runForEachTest { option.parse(listOf(format, value, "do-stuff")) }

                            it("indicates that parsing failed") {
                                assertThat(result, equalTo(OptionParsingResult.InvalidOption("Option '$format' requires a value to be provided, either in the form '$format=<key>=<value>' or '$format <key>=<value>'.")))
                            }
                        }
                    }

                    on("parsing a list of arguments where the option is given in the form '$format=(value)' but no value is provided after the equals sign") {
                        val result by runForEachTest { option.parse(listOf("$format=", "do-stuff")) }

                        it("indicates that parsing failed") {
                            assertThat(result, equalTo(OptionParsingResult.InvalidOption("Option '$format=' is in an invalid format, you must provide a value after '='.")))
                        }
                    }

                    on("parsing a list of arguments where the option is given in the form '$format (value)' but no second argument is provided") {
                        val result by runForEachTest { option.parse(listOf(format)) }

                        it("indicates that parsing failed") {
                            assertThat(result, equalTo(OptionParsingResult.InvalidOption("Option '$format' requires a value to be provided, either in the form '$format=<key>=<value>' or '$format <key>=<value>'.")))
                        }
                    }

                    given("a list of arguments with a key/value pair has already been parsed") {
                        beforeEachTest { option.parse(listOf(format, "key=value")) }

                        on("parsing a list of arguments where the option is given a second time with a different key") {
                            val result by runForEachTest { option.parse(listOf(format, "other_key=other_value")) }

                            it("indicates that parsing succeeded and that two arguments were consumed") {
                                assertThat(result, equalTo(OptionParsingResult.ReadOption(2)))
                            }

                            it("sets the option's value to include both key/value pairs") {
                                assertThat(option.getValue(mock(), mock()), equalTo(mapOf("key" to "value", "other_key" to "other_value")))
                            }
                        }

                        on("parsing a list of arguments where the option is given a second time with the same key") {
                            val result by runForEachTest { option.parse(listOf(format, "key=other_value")) }

                            it("indicates that parsing failed") {
                                assertThat(result, equalTo(OptionParsingResult.InvalidOption("Option '$format' does not allow duplicate values for the same key, but multiple values for 'key' have been given.")))
                            }
                        }
                    }
                }
            }

            on("not applying a value for the option") {
                val option = SingleValueMapOption(group, "option", "Some option.")

                it("returns an empty map") {
                    assertThat(option.getValue(mock(), mock()), equalTo(emptyMap()))
                }
            }
        }

        describe("checking the default value applied to the option") {
            val option by createForEachTest { SingleValueMapOption(group, "option", "Some option.") }

            given("the default value has not been overridden") {
                on("checking the default value for the option") {
                    it("does not return an error") {
                        assertThat(option.checkDefaultValue(), equalTo(DefaultApplicationResult.Succeeded))
                    }

                    it("reports that the value is the default") {
                        assertThat(option.valueSource, equalTo(OptionValueSource.Default))
                    }
                }
            }

            given("the default value has been overridden") {
                beforeEachTest {
                    option.parseValue(listOf("--option=a=b"))
                }

                on("checking the default value for the option") {
                    it("does not return an error") {
                        assertThat(option.checkDefaultValue(), equalTo(DefaultApplicationResult.Succeeded))
                    }

                    it("reports that the value is from the command line") {
                        assertThat(option.valueSource, equalTo(OptionValueSource.CommandLine))
                    }
                }
            }
        }

        describe("getting the help description for an option") {
            val option = SingleValueMapOption(group, "option", "Some option.")

            it("returns the provided description") {
                assertThat(option.descriptionForHelp, equalTo("Some option. Can be given multiple times."))
            }
        }
    }
})
