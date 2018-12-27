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

package batect.cli

import batect.os.PathResolutionResult
import batect.os.PathResolver
import batect.os.PathResolverFactory
import batect.os.PathType
import batect.testutils.equalTo
import batect.ui.OutputStyle
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object CommandLineOptionsParserSpec : Spek({
    describe("a command line interface") {
        val fileSystem = Jimfs.newFileSystem(Configuration.unix())

        val pathResolver = mock<PathResolver> {
            on { resolve("somefile.log") } doReturn PathResolutionResult.Resolved(fileSystem.getPath("/resolved/somefile.log"), PathType.File)
            on { resolve("somefile.yml") } doReturn PathResolutionResult.Resolved(fileSystem.getPath("/resolved/somefile.yml"), PathType.File)
        }

        val pathResolverFactory = mock<PathResolverFactory> {
            on { createResolverForCurrentDirectory() } doReturn pathResolver
        }

        given("no arguments") {
            on("parsing the command line") {
                val result = CommandLineOptionsParser(pathResolverFactory).parse(emptyList())

                it("returns an error message") {
                    assertThat(result, equalTo(CommandLineOptionsParsingResult.Failed("No task name provided.")))
                }
            }
        }

        given("a single argument for the task name") {
            on("parsing the command line") {
                val result = CommandLineOptionsParser(pathResolverFactory).parse(listOf("some-task"))

                it("returns a set of options with just the task name populated") {
                    assertThat(result, equalTo(CommandLineOptionsParsingResult.Succeeded(CommandLineOptions(
                        taskName = "some-task"
                    ))))
                }
            }
        }

        given("multiple arguments without a '--' prefix") {
            on("parsing the command line") {
                val result = CommandLineOptionsParser(pathResolverFactory).parse(listOf("some-task", "some-extra-arg"))

                it("returns an error message") {
                    assertThat(result, equalTo(CommandLineOptionsParsingResult.Failed(
                        "Too many arguments provided. The first extra argument is 'some-extra-arg'.\n" +
                            "To pass additional arguments to the task command, prefix them with '--', for example, './batect my-task -- --extra-option-1 --extra-option-2 value'."
                    )))
                }
            }
        }

        given("multiple arguments with a '--' prefix") {
            on("parsing the command line") {
                val result = CommandLineOptionsParser(pathResolverFactory).parse(listOf("some-task", "--", "some-extra-arg"))

                it("returns a set of options with the task name and additional arguments populated") {
                    assertThat(result, equalTo(CommandLineOptionsParsingResult.Succeeded(CommandLineOptions(
                        taskName = "some-task",
                        additionalTaskCommandArguments = listOf("some-extra-arg")
                    ))))
                }
            }
        }

        given("a flag followed by a single argument") {
            on("parsing the command line") {
                val result = CommandLineOptionsParser(pathResolverFactory).parse(listOf("--no-color", "some-task"))

                it("returns a set of options with the task name populated and the flag set") {
                    assertThat(result, equalTo(CommandLineOptionsParsingResult.Succeeded(CommandLineOptions(
                        disableColorOutput = true,
                        taskName = "some-task"
                    ))))
                }
            }
        }

        given("a flag followed by multiple arguments") {
            on("parsing the command line") {
                val result = CommandLineOptionsParser(pathResolverFactory).parse(listOf("--no-color", "some-task", "some-extra-arg"))

                it("returns an error message") {
                    assertThat(result, equalTo(CommandLineOptionsParsingResult.Failed(
                        "Too many arguments provided. The first extra argument is 'some-extra-arg'.\n" +
                            "To pass additional arguments to the task command, prefix them with '--', for example, './batect my-task -- --extra-option-1 --extra-option-2 value'."
                    )))
                }
            }
        }

        given("colour output has been disabled and fancy output mode has been selected") {
            on("parsing the command line") {
                val result = CommandLineOptionsParser(pathResolverFactory).parse(listOf("--no-color", "--output=fancy", "some-task", "some-extra-arg"))

                it("returns an error message") {
                    assertThat(result, equalTo(CommandLineOptionsParsingResult.Failed("Fancy output mode cannot be used when colored output has been disabled.")))
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
            listOf("--upgrade") to CommandLineOptions(runUpgrade = true),
            listOf("--upgrade", "some-task") to CommandLineOptions(runUpgrade = true),
            listOf("-f=somefile.yml", "some-task") to CommandLineOptions(configurationFileName = fileSystem.getPath("/resolved/somefile.yml"), taskName = "some-task"),
            listOf("--config-file=somefile.yml", "some-task") to CommandLineOptions(configurationFileName = fileSystem.getPath("/resolved/somefile.yml"), taskName = "some-task"),
            listOf("--log-file=somefile.log", "some-task") to CommandLineOptions(logFileName = fileSystem.getPath("/resolved/somefile.log"), taskName = "some-task"),
            listOf("--output=simple", "some-task") to CommandLineOptions(requestedOutputStyle = OutputStyle.Simple, taskName = "some-task"),
            listOf("--output=quiet", "some-task") to CommandLineOptions(requestedOutputStyle = OutputStyle.Quiet, taskName = "some-task"),
            listOf("--output=fancy", "some-task") to CommandLineOptions(requestedOutputStyle = OutputStyle.Fancy, taskName = "some-task"),
            listOf("--no-color", "some-task") to CommandLineOptions(disableColorOutput = true, taskName = "some-task"),
            listOf("--no-update-notification", "some-task") to CommandLineOptions(disableUpdateNotification = true, taskName = "some-task"),
            listOf("--level-of-parallelism=900", "some-task") to CommandLineOptions(levelOfParallelism = 900, taskName = "some-task"),
            listOf("-p=900", "some-task") to CommandLineOptions(levelOfParallelism = 900, taskName = "some-task"),
            listOf("--no-cleanup-after-failure", "some-task") to CommandLineOptions(disableCleanupAfterFailure = true, taskName = "some-task"),
            listOf("--no-proxy-vars", "some-task") to CommandLineOptions(dontPropagateProxyEnvironmentVariables = true, taskName = "some-task")
        ).forEach { args, expectedResult ->
            given("the arguments $args") {
                on("parsing the command line") {
                    val result = CommandLineOptionsParser(pathResolverFactory).parse(args)

                    it("returns a set of options with the expected options populated") {
                        assertThat(result, equalTo(CommandLineOptionsParsingResult.Succeeded(expectedResult)))
                    }
                }
            }
        }
    }
})
