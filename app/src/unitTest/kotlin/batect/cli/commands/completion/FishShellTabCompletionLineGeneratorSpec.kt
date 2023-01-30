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

package batect.cli.commands.completion

import batect.cli.options.FlagOption
import batect.cli.options.OptionGroup
import batect.cli.options.PathValueConverter
import batect.cli.options.SingleValueMapOption
import batect.cli.options.ValueConverters
import batect.cli.options.ValueOption
import batect.cli.options.defaultvalues.StaticDefaultValueProvider
import batect.testutils.equalTo
import batect.testutils.given
import batect.ui.OutputStyle
import com.natpryce.hamkrest.assertion.assertThat
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object FishShellTabCompletionLineGeneratorSpec : Spek({
    describe("generating completion script lines for Fish") {
        val generator = FishShellTabCompletionLineGenerator()
        val registerAs = "batect-1.2.3"
        val optionGroup = OptionGroup("Some group")

        given("a flag option") {
            given("it has only a long option") {
                val option = FlagOption(optionGroup, "some-option", "The option description", StaticDefaultValueProvider(false), null)

                it("generates a completion line with only the long option") {
                    assertThat(
                        generator.generate(option, registerAs),
                        equalTo("""complete -c $registerAs -l some-option --description 'The option description' --no-files --condition "not __fish_seen_argument -l some-option; and not __fish_seen_subcommand_from --""""),
                    )
                }
            }

            given("it has both a long and short option") {
                val option = FlagOption(optionGroup, "some-option", "The option description", StaticDefaultValueProvider(false), 's')

                it("generates a completion line with both forms of the option") {
                    assertThat(
                        generator.generate(option, registerAs),
                        equalTo("""complete -c $registerAs -l some-option -o s --description 'The option description' --no-files --condition "not __fish_seen_argument -l some-option -o s; and not __fish_seen_subcommand_from --""""),
                    )
                }
            }

            given("it has a description with a single quote in it") {
                val option = FlagOption(optionGroup, "some-option", "The option's description", StaticDefaultValueProvider(false), null)

                it("escapes the single quote") {
                    assertThat(
                        generator.generate(option, registerAs),
                        equalTo("""complete -c $registerAs -l some-option --description 'The option\'s description' --no-files --condition "not __fish_seen_argument -l some-option; and not __fish_seen_subcommand_from --""""),
                    )
                }
            }
        }

        given("a value option") {
            given("it accepts a string") {
                given("it has only a long option") {
                    val option = ValueOption(optionGroup, "some-option", "The option description", StaticDefaultValueProvider(""), ValueConverters.string)

                    it("generates a completion line with only the long option, specifying that the option requires a value") {
                        assertThat(
                            generator.generate(option, registerAs),
                            equalTo("""complete -c $registerAs -l some-option --description 'The option description' --no-files --condition "not __fish_seen_argument -l some-option; and not __fish_seen_subcommand_from --" --require-parameter"""),
                        )
                    }
                }

                given("it has both a long and short option") {
                    val option = ValueOption(optionGroup, "some-option", "The option description", StaticDefaultValueProvider(""), ValueConverters.string, 's')

                    it("generates a completion line with both forms of the option") {
                        assertThat(
                            generator.generate(option, registerAs),
                            equalTo(
                                """complete -c $registerAs -l some-option -o s --description 'The option description' --no-files --condition "not __fish_seen_argument -l some-option -o s; and not __fish_seen_subcommand_from --" --require-parameter""",
                            ),
                        )
                    }
                }
            }

            given("it accepts an enum") {
                val option = ValueOption(optionGroup, "some-option", "The option description", StaticDefaultValueProvider(OutputStyle.Fancy), ValueConverters.enum())

                it("generates a completion line with the possible enum values") {
                    assertThat(
                        generator.generate(option, registerAs),
                        equalTo(
                            """complete -c $registerAs -l some-option --description 'The option description' --no-files --condition "not __fish_seen_argument -l some-option; and not __fish_seen_subcommand_from --" --require-parameter -a "all fancy quiet simple"""",
                        ),
                    )
                }
            }

            given("it accepts a file or directory") {
                val option = ValueOption(optionGroup, "some-option", "The option description", StaticDefaultValueProvider(OutputStyle.Fancy), mock<PathValueConverter>())

                it("generates a completion line with the possible enum values") {
                    assertThat(
                        generator.generate(option, registerAs),
                        equalTo("""complete -c $registerAs -l some-option --description 'The option description' --force-files --condition "not __fish_seen_argument -l some-option; and not __fish_seen_subcommand_from --" --require-parameter"""),
                    )
                }
            }
        }

        given("a map option") {
            val option = SingleValueMapOption(optionGroup, "some-option", "The option description")

            it("generates a completion line that does not restrict the number of times the option is given") {
                assertThat(generator.generate(option, registerAs), equalTo("""complete -c $registerAs -l some-option --description 'The option description' --no-files --condition "not __fish_seen_subcommand_from --" --require-parameter"""))
            }
        }
    }
})
