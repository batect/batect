/*
    Copyright 2017-2021 Charles Korn.

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

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SetMultiValueOptionSpec : Spek({
    describe("a list value option") {
        val group = OptionGroup("the group")

        describe("parsing") {
            given("an option with short and long names") {
                val option by createForEachTest { SetMultiValueOption(group, "option", "Some option.", 'o') }

                listOf("--option", "-o").forEach { format ->
                    on("parsing a list of arguments where the option is specified in the form '$format value'") {
                        val result by runForEachTest { option.parse(listOf(format, "value", "do-stuff")) }

                        it("indicates that parsing succeeded and that two arguments were consumed") {
                            assertThat(result, equalTo(OptionParsingResult.ReadOption(2)))
                        }

                        it("sets the option's value") {
                            assertThat(option.getValue(mock(), mock()), equalTo(listOf("value")))
                        }

                        it("reports that the value is from the command line") {
                            assertThat(option.valueSource, equalTo(OptionValueSource.CommandLine))
                        }
                    }

                    on("parsing a list of arguments where the option is specified in the form '$format=value'") {
                        val result by runForEachTest { option.parse(listOf("$format=value", "do-stuff")) }

                        it("indicates that parsing succeeded and that one argument was consumed") {
                            assertThat(result, equalTo(OptionParsingResult.ReadOption(1)))
                        }

                        it("sets the option's value") {
                            assertThat(option.getValue(mock(), mock()), equalTo(listOf("value")))
                        }

                        it("reports that the value is from the command line") {
                            assertThat(option.valueSource, equalTo(OptionValueSource.CommandLine))
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
                            assertThat(result, equalTo(OptionParsingResult.InvalidOption("Option '$format' requires a value to be provided, either in the form '$format=<value1, value2, value3, ...>' or '$format <value1, value2, value3, ...>'.")))
                        }
                    }

                    given("a list of options where a value has been duplicated") {
                        on("parsing a list of arguments where the option is given a second time with the duplicate value") {
                            val result by runForEachTest { option.parse(listOf(format, "value1,value2,value3,value1")) }

                            it("indicates that parsing failed") {
                                assertThat(result, equalTo(OptionParsingResult.InvalidOption("Option '$format' does not allow duplicate values in the list.")))
                            }
                        }
                    }

                    given("a list of options where value have leading or trailing spaces") {
                        on("parsing a list of arguments") {
                            runForEachTest { option.parse(listOf(format, "  value1  ,value2,   value3,value4   ")) }

                            it("successfully parses and removes leading/trailing spaces") {
                                assertThat(option.getValue(mock(), mock()), equalTo(listOf("value1", "value2", "value3", "value4")))
                            }
                        }
                    }
                }
            }

            on("not applying a value for the option") {
                val option = SetMultiValueOption(group, "option", "Some option.")

                it("returns an empty list") {
                    assertThat(option.getValue(mock(), mock()), equalTo(emptyList()))
                }
            }
        }

        describe("getting the help description for an option") {
            val option = SetMultiValueOption(group, "option", "Some option.")

            it("returns the provided description") {
                assertThat(option.descriptionForHelp, equalTo("Some option. Can be provided multiple values."))
            }
        }
    }
})
