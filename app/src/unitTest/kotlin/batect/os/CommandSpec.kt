/*
   Copyright 2017-2019 Charles Korn.

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

import batect.testutils.given
import batect.testutils.on
import batect.testutils.withMessage
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object CommandSpec : Spek({
    describe("a command") {
        describe("adding arguments to a command") {
            val command = Command.parse("some-command")

            on("adding an empty list of arguments") {
                it("returns the original command") {
                    assertThat(command + emptyList(), equalTo(command))
                }
            }

            on("adding a list of arguments") {
                it("returns the concatenated original command and new arguments") {
                    assertThat(command + listOf("some-arg"), equalTo(Command.parse("some-command some-arg")))
                }
            }

            on("adding a list of arguments where an argument contains a space") {
                it("formats the resulting command as if the argument was surrounded with double quotes") {
                    assertThat(command + listOf("some arg"), equalTo(Command.parse("some-command \"some arg\"")))
                }
            }

            on("adding a list of arguments where an argument contains a double quote") {
                it("formats the resulting command as if the double quote was escaped with a slash") {
                    assertThat(command + listOf("""some"arg"""), equalTo(Command.parse("some-command some\\\"arg")))
                }
            }

            on("adding a list of arguments where an argument contains a single quote") {
                it("formats the resulting command as if the single quote was escaped with a slash") {
                    assertThat(command + listOf("""some'arg"""), equalTo(Command.parse("some-command some\\'arg")))
                }
            }
        }

        describe("parsing") {
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
            ).forEach { (originalCommand, expectedSplit) ->
                given("the command '$originalCommand'") {
                    on("parsing the command") {
                        val command = Command.parse(originalCommand)

                        it("returns a non-null result") {
                            assertThat(command, !absent<Command>())
                        }

                        it("generates the correct command line") {
                            assertThat(command.parsedCommand, equalTo(expectedSplit.asIterable()))
                        }

                        it("includes the original command line") {
                            assertThat(command.originalCommand, equalTo(originalCommand))
                        }
                    }
                }
            }

            mapOf(
                """echo "hello""" to "it contains an unbalanced double quote",
                """echo 'hello""" to "it contains an unbalanced single quote",
                """echo hello\""" to """it ends with a backslash (backslashes always escape the following character, for a literal backslash, use '\\')""",
                """echo "hello\""" to """it ends with a backslash (backslashes always escape the following character, for a literal backslash, use '\\')"""
            ).forEach { (command, expectedErrorMessage) ->
                given("the command '$command'") {
                    on("parsing the command") {
                        it("throws an exception with the message '$expectedErrorMessage'") {
                            assertThat({ Command.parse(command) },
                                throws<InvalidCommandLineException>(withMessage("Command `$command` is invalid: $expectedErrorMessage")))
                        }
                    }
                }
            }
        }
    }
})
