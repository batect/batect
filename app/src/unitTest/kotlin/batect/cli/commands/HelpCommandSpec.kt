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

package batect.cli.commands

import batect.cli.CommandLineOptionsParser
import batect.cli.options.DefaultApplicationResult
import batect.cli.options.OptionDefinition
import batect.cli.options.OptionGroup
import batect.cli.options.OptionParser
import batect.cli.options.OptionParsingResult
import batect.cli.options.OptionValueSource
import batect.os.ConsoleDimensions
import batect.os.Dimensions
import batect.testutils.given
import batect.testutils.withPlatformSpecificLineSeparator
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object HelpCommandSpec : Spek({
    describe("a help command") {
        fun createOption(group: OptionGroup, longName: String, description: String, acceptsValue: Boolean, shortName: Char? = null): OptionDefinition =
            object : OptionDefinition(group, longName, description, acceptsValue, shortName) {
                override fun parseValue(args: Iterable<String>): OptionParsingResult = throw NotImplementedError()
                override fun checkDefaultValue(): DefaultApplicationResult = DefaultApplicationResult.Succeeded
                override val valueSource: OptionValueSource
                    get() = throw NotImplementedError()

                override val descriptionForHelp: String = "$description (extra help info)"
                override val valueFormatForHelp: String = "some_custom_value_format"
            }

        given("and the root parser has some common options") {
            val output = ByteArrayOutputStream()
            val outputStream = PrintStream(output)
            val consoleDimensions = mock<ConsoleDimensions> {
                on { current } doReturn Dimensions(10, 95)
            }

            val group1 = OptionGroup("Group 1 options")
            val group2 = OptionGroup("Group 2 options")

            val options = mock<OptionParser> {
                on { getOptions() } doReturn setOf(
                    createOption(group2, "awesomeness-level", "Level of awesomeness to use.", true),
                    createOption(group2, "booster-level", "Level of boosters to use.", true),
                    createOption(group1, "file", "File name to use.", true, 'f'),
                    createOption(group1, "enable-extra-stuff", "Something you can enable if you want.", false)
                )
            }

            val parser = mock<CommandLineOptionsParser> {
                on { optionParser } doReturn options
            }

            val command = HelpCommand(parser, outputStream, consoleDimensions)
            val exitCode = command.run()

            it("prints help information, grouping the options and limiting the width of the output to the width of the console") {
                assertThat(output.toString(), equalTo("""
                        |Usage: batect [options] task [-- additional arguments to pass to task]
                        |
                        |Group 1 options:
                        |      --enable-extra-stuff                            Something you can enable if you want.
                        |                                                      (extra help info)
                        |  -f, --file=some_custom_value_format                 File name to use. (extra help info)
                        |
                        |Group 2 options:
                        |      --awesomeness-level=some_custom_value_format    Level of awesomeness to use. (extra help
                        |                                                      info)
                        |      --booster-level=some_custom_value_format        Level of boosters to use. (extra help
                        |                                                      info)
                        |
                        |For documentation and further information on batect, visit https://github.com/batect/batect.
                        |
                        |""".trimMargin().withPlatformSpecificLineSeparator()))
            }

            it("returns a non-zero exit code") {
                assertThat(exitCode, !equalTo(0))
            }
        }
    }
})
