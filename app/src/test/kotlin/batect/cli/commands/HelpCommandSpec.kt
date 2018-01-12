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

package batect.cli.commands

import batect.cli.CommandLineOptionsParser
import batect.cli.options.OptionDefinition
import batect.cli.options.OptionParser
import batect.cli.options.OptionParsingResult
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object HelpCommandSpec : Spek({
    describe("a help command") {
        fun createOption(longName: String, description: String, acceptsValue: Boolean, shortName: Char? = null): OptionDefinition =
            object : OptionDefinition(longName, description, acceptsValue, shortName) {
                override fun parseValue(args: Iterable<String>): OptionParsingResult = throw NotImplementedError()
                override val descriptionForHelp: String
                    get() = description + " (extra help info)"
            }

        given("and the root parser has some common options") {
            val output = ByteArrayOutputStream()
            val outputStream = PrintStream(output)

            val options = mock<OptionParser> {
                on { getOptions() } doReturn setOf(
                    createOption("awesomeness-level", "Level of awesomeness to use.", true),
                    createOption("booster-level", "Level of boosters to use.", true),
                    createOption("file", "File name to use.", true, 'f'),
                    createOption("enable-extra-stuff", "Something you can enable if you want.", false)
                )
            }

            val parser = mock<CommandLineOptionsParser> {
                on { optionParser } doReturn options
            }

            val command = HelpCommand(parser, outputStream)
            val exitCode = command.run()

            it("prints help information") {
                assertThat(output.toString(), equalTo("""
                        |Usage: batect [options] task [-- additional arguments to pass to task]
                        |
                        |Options:
                        |      --awesomeness-level=value    Level of awesomeness to use. (extra help info)
                        |      --booster-level=value        Level of boosters to use. (extra help info)
                        |      --enable-extra-stuff         Something you can enable if you want. (extra help info)
                        |  -f, --file=value                 File name to use. (extra help info)
                        |
                        |For documentation and further information on batect, visit https://github.com/charleskorn/batect.
                        |
                        |""".trimMargin()))
            }

            it("returns a non-zero exit code") {
                assertThat(exitCode, !equalTo(0))
            }
        }
    }
})
