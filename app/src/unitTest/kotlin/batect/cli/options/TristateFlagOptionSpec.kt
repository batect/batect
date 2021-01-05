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

import batect.cli.options.defaultvalues.DefaultValueProvider
import batect.cli.options.defaultvalues.PossibleValue
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TristateFlagOptionSpec : Spek({
    describe("a tri-state flag option") {
        val group = OptionGroup("the group")

        describe("parsing") {
            val defaultProvider = mock<DefaultValueProvider<Boolean?>> {
                onGeneric { value } doReturn PossibleValue.Valid<Boolean?>(true)
            }

            val option by createForEachTest { TristateFlagOption(group, "enable-extra-awesomeness", "Enable the extra awesome features.", defaultProvider) }

            on("the option not being provided") {
                it("gives the value from the default value provider") {
                    assertThat(option.getValue(mock(), mock()), equalTo(true))
                }

                it("reports that the value is the default") {
                    assertThat(option.valueSource, equalTo(OptionValueSource.Default))
                }
            }

            listOf("--enable-extra-awesomeness", "-a").forEach { format ->
                on("the option being provided in the format $format") {
                    val result by runForEachTest { option.parseValue(listOf(format)) }

                    it("gives the value as true") {
                        assertThat(option.getValue(mock(), mock()), equalTo(true))
                    }

                    it("returns that one argument was consumed") {
                        assertThat(result, equalTo(OptionParsingResult.ReadOption(1)))
                    }

                    it("reports that the value is from the command line") {
                        assertThat(option.valueSource, equalTo(OptionValueSource.CommandLine))
                    }
                }

                on("the option being provided with a value in the format $format=something") {
                    val result by runForEachTest { option.parseValue(listOf("$format=something")) }

                    it("returns that the argument is invalid") {
                        assertThat(result, equalTo(OptionParsingResult.InvalidOption("The option '$format' does not take a value.")))
                    }
                }
            }
        }

        describe("checking the default value applied to the option") {
            given("the default value is valid") {
                val defaultProvider = mock<DefaultValueProvider<Boolean>> {
                    onGeneric { value } doReturn PossibleValue.Valid(true)
                }

                val option by createForEachTest { FlagOption(group, "enable-extra-awesomeness", "Enable the extra awesome features.", defaultProvider) }

                on("checking the default value for the option") {
                    it("does not return an error") {
                        assertThat(option.checkDefaultValue(), equalTo(DefaultApplicationResult.Succeeded))
                    }
                }
            }

            given("the default value is invalid") {
                val defaultProvider = mock<DefaultValueProvider<Boolean>> {
                    onGeneric { value } doReturn PossibleValue.Invalid("The default value is invalid")
                }

                val option by createForEachTest { FlagOption(group, "enable-extra-awesomeness", "Enable the extra awesome features.", defaultProvider) }

                given("the default value has not been overridden") {
                    on("checking the default value for the option") {
                        it("returns an error") {
                            assertThat(option.checkDefaultValue(), equalTo(DefaultApplicationResult.Failed("The default value is invalid")))
                        }
                    }
                }

                given("the default value has been overridden") {
                    beforeEachTest {
                        option.parseValue(listOf("--enable-extra-awesomeness"))
                    }

                    on("checking the default value for the option") {
                        it("does not return an error") {
                            assertThat(option.checkDefaultValue(), equalTo(DefaultApplicationResult.Succeeded))
                        }
                    }
                }
            }
        }

        describe("getting the help description for an option") {
            on("the default value provider not giving any extra information") {
                val defaultProvider = mock<DefaultValueProvider<Boolean>> {
                    onGeneric { value } doReturn PossibleValue.Valid(true)
                    onGeneric { description } doReturn ""
                }

                val option = FlagOption(group, "enable-extra-awesomeness", "Enable the extra awesome features.", defaultProvider)

                it("returns the original description") {
                    assertThat(option.descriptionForHelp, equalTo("Enable the extra awesome features."))
                }
            }

            on("the default value provider giving extra information") {
                val defaultProvider = mock<DefaultValueProvider<Boolean>> {
                    onGeneric { value } doReturn PossibleValue.Valid(true)
                    onGeneric { description } doReturn "Defaults to '1234' if not set."
                }

                val option = FlagOption(group, "enable-extra-awesomeness", "Enable the extra awesome features.", defaultProvider)

                it("returns the original description with the additional information from the default value provider") {
                    assertThat(option.descriptionForHelp, equalTo("Enable the extra awesome features. Defaults to '1234' if not set."))
                }
            }
        }
    }
})
