/*
   Copyright 2017-2018 Charles Korn.

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

import batect.cli.options.defaultvalues.DefaultValueProvider
import batect.cli.options.defaultvalues.StaticDefaultValueProvider
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ValueOptionSpec : Spek({
    describe("a value option") {
        val converter = { value: String ->
            when (value) {
                "valid-value" -> ValueConversionResult.ConversionSucceeded(123)
                else -> ValueConversionResult.ConversionFailed<Int>("'$value' is not a acceptable value.")
            }
        }

        describe("parsing") {
            given("an option with short and long names") {
                val valueConverter = { value: String ->
                    when (value) {
                        "invalid-thing" -> ValueConversionResult.ConversionFailed<String>("that's not allowed")
                        else -> ValueConversionResult.ConversionSucceeded(value)
                    }
                }

                val option by createForEachTest { ValueOption("value", "The value", StaticDefaultValueProvider("default-value"), valueConverter, 'v') }

                listOf("--value", "-v").forEach { format ->
                    on("parsing a list of arguments where the option is specified in the form '$format thing'") {
                        val result = option.parse(listOf(format, "thing", "do-stuff"))

                        it("indicates that parsing succeeded and that two arguments were consumed") {
                            assertThat(result, equalTo(OptionParsingResult.ReadOption(2)))
                        }

                        it("sets the option's value") {
                            assertThat(option.value, equalTo("thing"))
                        }
                    }

                    on("parsing a list of arguments where the option is specified in the form '$format=thing'") {
                        val result = option.parse(listOf("$format=thing", "do-stuff"))

                        it("indicates that parsing succeeded and that one argument was consumed") {
                            assertThat(result, equalTo(OptionParsingResult.ReadOption(1)))
                        }

                        it("sets the option's value") {
                            assertThat(option.value, equalTo("thing"))
                        }
                    }

                    on("parsing a list of arguments where the option is specified in a valid form but the value is not valid") {
                        val result = option.parse(listOf(format, "invalid-thing", "do-stuff"))

                        it("indicates that parsing failed") {
                            assertThat(result, equalTo(OptionParsingResult.InvalidOption("The value 'invalid-thing' for option '$format' is invalid: that's not allowed")))
                        }
                    }

                    on("parsing a list of arguments where the option is given in the form '$format=thing' but no value is provided after the equals sign") {
                        val result = option.parse(listOf("$format=", "do-stuff"))

                        it("indicates that parsing failed") {
                            assertThat(result, equalTo(OptionParsingResult.InvalidOption("Option '$format=' is in an invalid format, you must provide a value after '='.")))
                        }
                    }

                    on("parsing a list of arguments where the option is given in the form '$format thing' but no second argument is provided") {
                        val result = option.parse(listOf(format))

                        it("indicates that parsing failed") {
                            assertThat(result, equalTo(OptionParsingResult.InvalidOption("Option '$format' requires a value to be provided, either in the form '$format=<value>' or '$format <value>'.")))
                        }
                    }
                }
            }
        }

        on("not applying a value for the option") {
            val option = ValueOption("value", "The value", StaticDefaultValueProvider(9999), ValueConverters::positiveInteger, 'v')

            it("returns the default value") {
                assertThat(option.value, equalTo(9999))
            }
        }

        describe("getting the help description for an option") {
            on("the default value provider not giving any extra information") {
                val defaultProvider = mock<DefaultValueProvider<Int>> {
                    onGeneric { description } doReturn ""
                }

                val option = ValueOption("option", "Some integer option", defaultProvider, converter)

                it("returns the original description") {
                    assertThat(option.descriptionForHelp, equalTo("Some integer option"))
                }
            }

            on("the default value provider giving extra information") {
                val defaultProvider = mock<DefaultValueProvider<Int>> {
                    onGeneric { description } doReturn "defaults to '1234' if not set"
                }

                val option = ValueOption("option", "Some integer option", defaultProvider, converter)

                it("returns the original description with the additional information from the default value provider") {
                    assertThat(option.descriptionForHelp, equalTo("Some integer option (defaults to '1234' if not set)"))
                }
            }
        }
    }
})
