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

package batect.cli.commands

import batect.cli.CommandLineOptions
import batect.config.ContainerMap
import batect.config.RawConfiguration
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.config.io.ConfigurationLoadResult
import batect.config.io.ConfigurationLoader
import batect.os.Command
import batect.testutils.createForEachTest
import batect.testutils.runForEachTest
import batect.testutils.withPlatformSpecificLineSeparator
import batect.ui.OutputStyle
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object ListTasksCommandSpec : Spek({
    describe("a 'list tasks' command") {
        val taskRunConfig = TaskRunConfiguration("some-container", Command.parse("dont-care"))

        val fileSystem = Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix())
        val configFilePath = fileSystem.getPath("config.yml")
        val configLoader by createForEachTest { mock<ConfigurationLoader>() }
        val commandLineOptions by createForEachTest {
            mock<CommandLineOptions> {
                on { configurationFileName } doReturn configFilePath
            }
        }

        val output by createForEachTest { ByteArrayOutputStream() }
        val command by createForEachTest { ListTasksCommand(configLoader, commandLineOptions, PrintStream(output)) }

        fun Suite.whenNotRunningWithQuietOutputModeItProducesOutput(expectedOutput: String) {
            describe("when not running in quiet output mode") {
                beforeEachTest { whenever(commandLineOptions.requestedOutputStyle).doReturn(OutputStyle.Fancy) }

                val exitCode by runForEachTest { command.run() }

                it("prints the tasks in the expected order and format") {
                    assertThat(output.toString(), equalTo(expectedOutput.withPlatformSpecificLineSeparator()))
                }

                it("returns a zero exit code") {
                    assertThat(exitCode, equalTo(0))
                }
            }
        }

        fun Suite.whenRunningWithQuietOutputModeItProducesOutput(expectedOutput: String) {
            describe("when running in quiet output mode") {
                beforeEachTest { whenever(commandLineOptions.requestedOutputStyle).doReturn(OutputStyle.Quiet) }

                val exitCode by runForEachTest { command.run() }

                it("prints the tasks ungrouped, sorted alphabetically and with their descriptions, if any") {
                    assertThat(output.toString(), equalTo(expectedOutput.withPlatformSpecificLineSeparator()))
                }

                it("returns a zero exit code") {
                    assertThat(exitCode, equalTo(0))
                }
            }
        }

        describe("when invoked with a configuration file with no groups defined") {
            val task1 = Task("first-task", taskRunConfig)
            val task2 = Task("other-task", taskRunConfig)
            val task3 = Task("another-task-with-a-description", taskRunConfig, "do the thing")
            val config = RawConfiguration("the_project", TaskMap(task1, task2, task3), ContainerMap())

            beforeEachTest { whenever(configLoader.loadConfig(configFilePath)).doReturn(ConfigurationLoadResult(config, emptySet())) }

            describe("when the configuration file can be loaded") {
                whenNotRunningWithQuietOutputModeItProducesOutput(
                    """
                    |Available tasks:
                    |- another-task-with-a-description: do the thing
                    |- first-task
                    |- other-task
                    |
                    """.trimMargin()
                )

                whenRunningWithQuietOutputModeItProducesOutput(
                    """
                    |another-task-with-a-description${'\t'}do the thing
                    |first-task
                    |other-task
                    |
                    """.trimMargin()
                )
            }
        }

        describe("when invoked with a configuration file with one group defined") {
            val task1 = Task("first-task", taskRunConfig, group = "Build tasks")
            val task2 = Task("other-task", taskRunConfig, group = "Build tasks")
            val task3 = Task("another-task-with-a-description", taskRunConfig, "do the thing", group = "Build tasks")
            val config = RawConfiguration("the_project", TaskMap(task1, task2, task3), ContainerMap())

            beforeEachTest { whenever(configLoader.loadConfig(configFilePath)).doReturn(ConfigurationLoadResult(config, emptySet())) }

            describe("when the configuration file can be loaded") {
                whenNotRunningWithQuietOutputModeItProducesOutput(
                    """
                    |Build tasks:
                    |- another-task-with-a-description: do the thing
                    |- first-task
                    |- other-task
                    |
                    """.trimMargin()
                )

                whenRunningWithQuietOutputModeItProducesOutput(
                    """
                    |another-task-with-a-description${'\t'}do the thing
                    |first-task
                    |other-task
                    |
                    """.trimMargin()
                )
            }
        }

        describe("when invoked with a configuration file with multiple groups defined") {
            val task1 = Task("first-task", taskRunConfig, group = "Test tasks")
            val task2 = Task("other-task", taskRunConfig, group = "Build tasks")
            val task3 = Task("another-task-with-a-description", taskRunConfig, "do the thing", group = "Build tasks")
            val config = RawConfiguration("the_project", TaskMap(task1, task2, task3), ContainerMap())

            beforeEachTest { whenever(configLoader.loadConfig(configFilePath)).doReturn(ConfigurationLoadResult(config, emptySet())) }

            describe("when the configuration file can be loaded") {
                whenNotRunningWithQuietOutputModeItProducesOutput(
                    """
                    |Build tasks:
                    |- another-task-with-a-description: do the thing
                    |- other-task
                    |
                    |Test tasks:
                    |- first-task
                    |
                    """.trimMargin()
                )

                whenRunningWithQuietOutputModeItProducesOutput(
                    """
                    |another-task-with-a-description${'\t'}do the thing
                    |first-task
                    |other-task
                    |
                    """.trimMargin()
                )
            }
        }

        describe("when invoked with a configuration file where some tasks do not have a group") {
            val task1 = Task("first-task", taskRunConfig, group = "")
            val task2 = Task("other-task", taskRunConfig, group = "Build tasks")
            val task3 = Task("another-task-with-a-description", taskRunConfig, "do the thing", group = "Build tasks")
            val config = RawConfiguration("the_project", TaskMap(task1, task2, task3), ContainerMap())

            beforeEachTest { whenever(configLoader.loadConfig(configFilePath)).doReturn(ConfigurationLoadResult(config, emptySet())) }

            describe("when the configuration file can be loaded") {
                whenNotRunningWithQuietOutputModeItProducesOutput(
                    """
                    |Build tasks:
                    |- another-task-with-a-description: do the thing
                    |- other-task
                    |
                    |Ungrouped tasks:
                    |- first-task
                    |
                    """.trimMargin()
                )

                whenRunningWithQuietOutputModeItProducesOutput(
                    """
                    |another-task-with-a-description${'\t'}do the thing
                    |first-task
                    |other-task
                    |
                    """.trimMargin()
                )
            }
        }
    }
})
