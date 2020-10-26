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

package batect.cli.commands.completion

import batect.cli.CommandLineOptions
import batect.cli.CommandLineOptionsParser
import batect.os.HostEnvironmentVariables
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object GenerateShellTabCompletionScriptCommandSpec : Spek({
    describe("a 'generate shell tab completion script' command") {
        val commandLineOptions = CommandLineOptions(generateShellTabCompletion = KnownShell.Fish)
        val optionsParser by createForEachTest { mock<CommandLineOptionsParser>() }
        val outputStream by createForEachTest { ByteArrayOutputStream() }
        val lineGenerator by createForEachTest { mock<FishShellTabCompletionLineGenerator>() }

        given("the 'BATECT_COMPLETION_PROXY_REGISTER_AS' environment variable is not set") {
            val environmentVariables by createForEachTest { HostEnvironmentVariables() }
            val command by createForEachTest { GenerateShellTabCompletionScriptCommand(commandLineOptions, optionsParser, lineGenerator, PrintStream(outputStream), environmentVariables) }

            it("throws an appropriate exception") {
                assertThat({ command.run() }, throws(withMessage("'BATECT_COMPLETION_PROXY_REGISTER_AS' environment variable not set.")))
            }
        }

        // The rest of this class is tested primarily by the tests in the app/src/completionTest directory.
    }
})
