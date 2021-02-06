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

package batect.cli.commands.completion

import batect.cli.options.DirectoryPathValueConverter
import batect.cli.options.FilePathValueConverter
import batect.cli.options.FlagOption
import batect.cli.options.MapOption
import batect.cli.options.OptionGroup
import batect.cli.options.ValueConverters
import batect.cli.options.ValueOption
import batect.cli.options.defaultvalues.StaticDefaultValueProvider
import batect.testutils.equalTo
import batect.testutils.given
import batect.ui.OutputStyle
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ZshShellTabCompletionOptionGeneratorSpec : Spek({
    describe("generating completion script options for Zsh") {
        val generator = ZshShellTabCompletionOptionGenerator()
        val optionGroup = OptionGroup("Some group")

        given("a flag option") {
            given("it has only a long option") {
                val option = FlagOption(optionGroup, "some-option", "Not important", StaticDefaultValueProvider(false), null)

                it("generates a single completion line with only the long option") {
                    assertThat(generator.generate(option), equalTo(setOf("--some-option")))
                }
            }

            given("it has both a long and short option") {
                val option = FlagOption(optionGroup, "some-option", "Not important", StaticDefaultValueProvider(false), 's')

                it("generates completion lines with both forms of the option") {
                    assertThat(generator.generate(option), equalTo(setOf("(-s)--some-option", "(--some-option)-s")))
                }
            }
        }

        given("a value option") {
            given("it accepts a string") {
                given("it has only a long option") {
                    val option = ValueOption(optionGroup, "some-option", "Not important", StaticDefaultValueProvider(""), ValueConverters.string)

                    it("generates a single completion line with only the long option, specifying that the option requires a value") {
                        assertThat(generator.generate(option), equalTo(setOf("--some-option=::( )")))
                    }
                }

                given("it has both a long and short option") {
                    val option = ValueOption(optionGroup, "some-option", "Not important", StaticDefaultValueProvider(""), ValueConverters.string, 's')

                    it("generates completion lines with both forms of the option") {
                        assertThat(generator.generate(option), equalTo(setOf("(-s)--some-option=::( )", "(--some-option)-s=::( )")))
                    }
                }
            }

            given("it accepts an enum") {
                val option = ValueOption(optionGroup, "some-option", "Not important", StaticDefaultValueProvider(OutputStyle.Fancy), ValueConverters.enum())

                it("generates a completion line with the possible enum values") {
                    assertThat(generator.generate(option), equalTo(setOf("--some-option=:value:(all fancy quiet simple)")))
                }
            }

            given("it accepts a file") {
                val option = ValueOption(optionGroup, "some-option", "Not important", StaticDefaultValueProvider(OutputStyle.Fancy), mock<FilePathValueConverter>())

                it("generates a completion line specifying that the option takes a file") {
                    assertThat(generator.generate(option), equalTo(setOf("--some-option=:file name:_files")))
                }
            }

            given("it accepts a directory") {
                val option = ValueOption(optionGroup, "some-option", "Not important", StaticDefaultValueProvider(OutputStyle.Fancy), mock<DirectoryPathValueConverter>())

                it("generates a completion line specifying that the option takes a directory") {
                    assertThat(generator.generate(option), equalTo(setOf("--some-option=:directory name:_files -/")))
                }
            }
        }

        given("a map option") {
            given("it has only a long option") {
                val option = MapOption(optionGroup, "some-option", "Not important")

                it("generates a single completion line with only the long option, specifying that the option can be repeated") {
                    assertThat(generator.generate(option), equalTo(setOf("*--some-option=::( )")))
                }
            }

            given("it has both a long and short option") {
                val option = MapOption(optionGroup, "some-option", "Not important", 's')

                it("generates completion lines with both forms of the option") {
                    assertThat(generator.generate(option), equalTo(setOf("(-s)*--some-option=::( )", "(--some-option)*-s=::( )")))
                }
            }
        }
    }
})
