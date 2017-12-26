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

package batect.os

import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object CommandParserSpec : Spek({
    describe("a command parser") {
        val parser = CommandParser()

        // FIXME This stuff is hard and there are lots of edge cases. Surely there's a better way...
        // References:
        // - https://www.gnu.org/software/bash/manual/html_node/Quoting.html
        // - http://www.grymoire.com/Unix/Quote.html
        mapOf(
            "echo hello" to listOf("echo", "hello"),
            "echo  hello" to listOf("echo", "hello"),
            """echo "hello world"""" to listOf("echo", "hello world"),
            """echo 'hello world'""" to listOf("echo", "hello world"),
            """echo hello\ world""" to listOf("echo", "hello world"),
            """echo 'hello "world"'""" to listOf("echo", """hello "world""""),
            """echo "hello 'world'"""" to listOf("echo", "hello 'world'"),
            """echo "hello \"world\""""" to listOf("echo", """hello "world""""),
            """echo "hello 'world'"""" to listOf("echo", "hello 'world'"),
            """echo 'hello "world"'""" to listOf("echo", """hello "world""""),
            """echo can\'t""" to listOf("echo", "can't"),
            // This next example comes from http://stackoverflow.com/a/28640859/1668119
            """sh -c 'echo "\"un'\''kno\"wn\$\$\$'\'' with \$\"\$\$. \"zzz\""'""" to listOf("sh", "-c", """echo "\"un'kno\"wn\$\$\$' with \$\"\$\$. \"zzz\""""")
        ).forEach { command, expectedSplit ->
            given("the command '$command'") {
                on("parsing the command") {
                    val commandLine = parser.parse(command)

                    it("generates the correct command line") {
                        assertThat(commandLine, equalTo(expectedSplit.asIterable()))
                    }
                }
            }
        }

        mapOf(
            """echo "hello""" to "it contains an unbalanced double quote",
            """echo 'hello""" to "it contains an unbalanced single quote",
            """echo hello\""" to """it ends with a backslash (backslashes always escape the following character, for a literal backslash, use '\\')""",
            """echo "hello\""" to """it ends with a backslash (backslashes always escape the following character, for a literal backslash, use '\\')"""
        ).forEach { command, expectedErrorMessage ->
            given("the command '$command'") {
                on("parsing the command") {
                    it("throws an exception with the message '$expectedErrorMessage'") {
                        assertThat({ parser.parse(command) },
                            throws<InvalidCommandLineException>(withMessage("Command line `$command` is invalid: $expectedErrorMessage")))
                    }
                }
            }
        }
    }
})
