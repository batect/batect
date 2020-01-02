/*
   Copyright 2017-2020 Charles Korn.

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

import batect.cli.options.defaultvalues.StaticDefaultValueProvider
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object OptionParserSpec : Spek({
    describe("an option parser") {
        describe("parsing") {
            given("a parser with no options") {
                val parser by createForEachTest { OptionParser() }

                on("parsing an empty list of arguments") {
                    val result by runForEachTest { parser.parseOptions(emptyList()) }

                    it("indicates that parsing succeeded and that no arguments were consumed") {
                        assertThat(result, equalTo(OptionsParsingResult.ReadOptions(0)))
                    }
                }

                on("parsing a non-empty list of arguments") {
                    val result by runForEachTest { parser.parseOptions(listOf("some-argument")) }

                    it("indicates that parsing succeeded and that no arguments were consumed") {
                        assertThat(result, equalTo(OptionsParsingResult.ReadOptions(0)))
                    }
                }
            }

            given("a parser with a single value option with a short name") {
                val parser by createForEachTest { OptionParser() }
                val option by createForEachTest {
                    mock<OptionDefinition> {
                        on { longName } doReturn "value"
                        on { longOption } doReturn "--value"
                        on { shortName } doReturn 'v'
                        on { shortOption } doReturn "-v"
                        on { checkDefaultValue() } doReturn DefaultApplicationResult.Succeeded
                    }
                }

                beforeEachTest {
                    parser.addOption(option)
                }

                on("parsing an empty list of arguments") {
                    given("the default value for the option is valid") {
                        val result by runForEachTest { parser.parseOptions(emptyList()) }

                        it("indicates that parsing succeeded and that no arguments were consumed") {
                            assertThat(result, equalTo(OptionsParsingResult.ReadOptions(0)))
                        }

                        it("does not ask the option to parse a value") {
                            verify(option, never()).parse(any())
                        }
                    }

                    given("the default value for the option is not valid") {
                        beforeEachTest {
                            whenever(option.checkDefaultValue()).doReturn(DefaultApplicationResult.Failed("The default value is not valid"))
                        }

                        val result by runForEachTest { parser.parseOptions(emptyList()) }

                        it("indicates that parsing failed") {
                            assertThat(result, equalTo(OptionsParsingResult.InvalidOptions("The default value for the --value option is invalid: The default value is not valid")))
                        }
                    }
                }

                on("parsing a list of arguments where the option is not specified") {
                    val result by runForEachTest { parser.parseOptions(listOf("do-stuff")) }

                    it("indicates that parsing succeeded and that no arguments were consumed") {
                        assertThat(result, equalTo(OptionsParsingResult.ReadOptions(0)))
                    }

                    it("does not ask the option to parse a value") {
                        verify(option, never()).parse(any())
                    }
                }

                on("parsing a list of arguments where an unknown argument is specified") {
                    val result by runForEachTest { parser.parseOptions(listOf("--something-else")) }

                    it("indicates that parsing succeeded and that no arguments were consumed") {
                        assertThat(result, equalTo(OptionsParsingResult.InvalidOptions("Invalid option '--something-else'. Run './batect --help' for a list of valid options.")))
                    }

                    it("does not ask the option to parse a value") {
                        verify(option, never()).parse(any())
                    }
                }

                listOf("--value", "-v").forEach { format ->
                    describe("parsing a list of arguments where the argument is specified in the form '$format'") {
                        on("when the option indicates that parsing succeeded and a single argument was consumed") {
                            beforeEachTest { whenever(option.parse(any())).doReturn(OptionParsingResult.ReadOption(1)) }

                            val result by runForEachTest { parser.parseOptions(listOf(format)) }

                            it("indicates that parsing succeeded and that one argument was consumed") {
                                assertThat(result, equalTo(OptionsParsingResult.ReadOptions(1)))
                            }

                            it("asks the option to parse a value from the correct list of arguments") {
                                verify(option).parse(listOf(format))
                            }
                        }

                        on("when the option indicates that parsing succeeded and two arguments were consumed") {
                            beforeEachTest { whenever(option.parse(any())).doReturn(OptionParsingResult.ReadOption(2)) }

                            val result by runForEachTest { parser.parseOptions(listOf(format, "some-other-arg")) }

                            it("indicates that parsing succeeded and that two arguments were consumed") {
                                assertThat(result, equalTo(OptionsParsingResult.ReadOptions(2)))
                            }

                            it("asks the option to parse a value from the correct list of arguments") {
                                verify(option).parse(listOf(format, "some-other-arg"))
                            }
                        }

                        on("when the option indicates that parsing failed") {
                            beforeEachTest { whenever(option.parse(any())).doReturn(OptionParsingResult.InvalidOption("That's not allowed")) }

                            val result by runForEachTest { parser.parseOptions(listOf(format)) }

                            it("indicates that parsing failed") {
                                assertThat(result, equalTo(OptionsParsingResult.InvalidOptions("That's not allowed")))
                            }

                            it("asks the option to parse a value from the correct list of arguments") {
                                verify(option).parse(listOf(format))
                            }
                        }

                        on("when the option is specified multiple times") {
                            beforeEachTest {
                                whenever(option.parse(listOf(format, "something", format, format, "some-other-arg"))).doReturn(OptionParsingResult.ReadOption(2))
                                whenever(option.parse(listOf(format, format, "some-other-arg"))).doReturn(OptionParsingResult.ReadOption(1))
                                whenever(option.parse(listOf(format, "some-other-arg"))).doReturn(OptionParsingResult.ReadOption(1))
                            }

                            val result by runForEachTest { parser.parseOptions(listOf(format, "something", format, format, "some-other-arg")) }

                            it("indicates that parsing succeeded and that four arguments were consumed") {
                                assertThat(result, equalTo(OptionsParsingResult.ReadOptions(4)))
                            }

                            it("asks the option to parse values from the correct list of arguments each time") {
                                verify(option).parse(listOf(format, "something", format, format, "some-other-arg"))
                                verify(option).parse(listOf(format, format, "some-other-arg"))
                                verify(option).parse(listOf(format, "some-other-arg"))
                            }
                        }
                    }
                }
            }
        }

        describe("adding options") {
            given("a parser with a single value option with a short name") {
                val parser by createForEachTest { OptionParser() }
                val defaultValueProvider = StaticDefaultValueProvider<String?>(null)
                val option = ValueOption("value", "The value", defaultValueProvider, ValueConverters::string, 'v')
                beforeEachTest { parser.addOption(option) }

                on("getting the list of all options") {
                    val options by runForEachTest { parser.getOptions() }

                    it("returns a list with that single option") {
                        assertThat(options, equalTo(setOf<OptionDefinition>(option)))
                    }
                }

                on("attempting to add another option with the same name") {
                    it("throws an exception") {
                        assertThat({ parser.addOption(ValueOption("value", "The other value", defaultValueProvider, ValueConverters::string)) },
                            throws<IllegalArgumentException>(withMessage("An option with the name 'value' has already been added.")))
                    }
                }

                on("attempting to add another option with the same short name") {
                    it("throws an exception") {
                        assertThat({ parser.addOption(ValueOption("other-value", "The other value", defaultValueProvider, ValueConverters::string, 'v')) },
                            throws<IllegalArgumentException>(withMessage("An option with the name 'v' has already been added.")))
                    }
                }
            }
        }
    }
})
