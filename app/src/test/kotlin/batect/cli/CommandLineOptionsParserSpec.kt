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

package batect.cli

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object CommandLineOptionsParserSpec : Spek({
    describe("a command line interface") {
        given("no arguments") {
            on("parsing the command line") {
                val result = CommandLineOptionsParser().parse(emptyList())

                it("returns an error message") {
                    assertThat(result, equalTo<CommandLineOptionsParsingResult>(CommandLineOptionsParsingResult.Failed("No task name provided.")))
                }
            }
        }

        given("a single argument for the task name") {
            on("parsing the command line") {
                val result = CommandLineOptionsParser().parse(listOf("some-task"))

                it("returns a set of options with just the task name populated") {
                    assertThat(result, equalTo<CommandLineOptionsParsingResult>(CommandLineOptionsParsingResult.Succeeded(CommandLineOptions(
                        taskName = "some-task"
                    ))))
                }
            }
        }

        given("multiple arguments") {
            on("parsing the command line") {
                val result = CommandLineOptionsParser().parse(listOf("some-task", "some-extra-arg"))

                it("returns an error message") {
                    assertThat(result, equalTo<CommandLineOptionsParsingResult>(CommandLineOptionsParsingResult.Failed("Too many arguments provided. The first extra argument is 'some-extra-arg'.")))
                }
            }
        }

        given("a flag followed by a single argument") {
            on("parsing the command line") {
                val result = CommandLineOptionsParser().parse(listOf("--quiet", "some-task"))

                it("returns a set of options with the task name populated and the flag set") {
                    assertThat(result, equalTo<CommandLineOptionsParsingResult>(CommandLineOptionsParsingResult.Succeeded(CommandLineOptions(
                        forceQuietOutputMode = true,
                        taskName = "some-task"
                    ))))
                }
            }
        }

        given("a flag followed by multiple arguments") {
            on("parsing the command line") {
                val result = CommandLineOptionsParser().parse(listOf("--quiet", "some-task", "some-extra-arg"))

                it("returns an error message") {
                    assertThat(result, equalTo<CommandLineOptionsParsingResult>(CommandLineOptionsParsingResult.Failed("Too many arguments provided. The first extra argument is 'some-extra-arg'.")))
                }
            }
        }

        mapOf(
            listOf("--help") to CommandLineOptions(showHelp = true),
            listOf("--help", "some-task") to CommandLineOptions(showHelp = true),
            listOf("--version") to CommandLineOptions(showVersionInfo = true),
            listOf("--version", "some-task") to CommandLineOptions(showVersionInfo = true),
            listOf("--list-tasks") to CommandLineOptions(listTasks = true),
            listOf("--list-tasks", "some-task") to CommandLineOptions(listTasks = true),
            listOf("-f=somefile.yml", "some-task") to CommandLineOptions(configurationFileName = "somefile.yml", taskName = "some-task"),
            listOf("--config-file=somefile.yml", "some-task") to CommandLineOptions(configurationFileName = "somefile.yml", taskName = "some-task"),
            listOf("--log-file=somefile.log", "some-task") to CommandLineOptions(logFileName = "somefile.log", taskName = "some-task"),
            listOf("--simple-output", "some-task") to CommandLineOptions(forceSimpleOutputMode = true, taskName = "some-task"),
            listOf("--quiet", "some-task") to CommandLineOptions(forceQuietOutputMode = true, taskName = "some-task"),
            listOf("--no-color", "some-task") to CommandLineOptions(disableColorOutput = true, forceSimpleOutputMode = true, taskName = "some-task"),
            listOf("--no-update-notification", "some-task") to CommandLineOptions(disableUpdateNotification = true, taskName = "some-task"),
            listOf("--level-of-parallelism=900", "some-task") to CommandLineOptions(levelOfParallelism = 900, taskName = "some-task"),
            listOf("-p=900", "some-task") to CommandLineOptions(levelOfParallelism = 900, taskName = "some-task"),
            listOf("--no-cleanup-after-failure", "some-task") to CommandLineOptions(disableCleanupAfterFailure = true, taskName = "some-task"),
            listOf("--no-proxy-vars", "some-task") to CommandLineOptions(dontPropagateProxyEnvironmentVariables = true, taskName = "some-task")
        ).forEach { args, expectedResult ->
            given("the arguments $args") {
                on("parsing the command line") {
                    val result = CommandLineOptionsParser().parse(args)

                    it("returns a set of options with the expected options populated") {
                        assertThat(result, equalTo<CommandLineOptionsParsingResult>(CommandLineOptionsParsingResult.Succeeded(expectedResult)))
                    }
                }
            }
        }
    }
})
