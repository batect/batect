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

import batect.config.Configuration
import batect.config.ContainerMap
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.config.io.ConfigurationLoader
import batect.os.Command
import batect.testutils.createForEachTest
import batect.testutils.runForEachTest
import batect.testutils.withPlatformSpecificLineSeparator
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ListTasksCommandSpec : Spek({
    describe("a 'list tasks' command") {
        val fileSystem = Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix())
        val configFile = fileSystem.getPath("config.yml")
        val taskRunConfig = TaskRunConfiguration("some-container", Command.parse("dont-care"))

        describe("when invoked with a configuration file with no groups defined") {
            val task1 = Task("first-task", taskRunConfig)
            val task2 = Task("other-task", taskRunConfig)
            val task3 = Task("another-task-with-a-description", taskRunConfig, "do the thing")
            val config = Configuration("the_project", TaskMap(task1, task2, task3), ContainerMap())

            val configLoader by createForEachTest {
                mock<ConfigurationLoader> {
                    on { loadConfig(configFile) } doReturn config
                }
            }

            val output by createForEachTest { ByteArrayOutputStream() }
            val command by createForEachTest { ListTasksCommand(configFile, configLoader, PrintStream(output)) }

            describe("when the configuration file can be loaded") {
                val exitCode by runForEachTest { command.run() }

                it("prints the names of the available tasks in alphabetical order") {
                    assertThat(
                        output.toString(), equalTo(
                            """
                                |Available tasks:
                                |- another-task-with-a-description: do the thing
                                |- first-task
                                |- other-task
                                |
                            """.trimMargin().withPlatformSpecificLineSeparator()
                        )
                    )
                }

                it("returns a zero exit code") {
                    assertThat(exitCode, equalTo(0))
                }
            }
        }

        describe("when invoked with a configuration file with one group defined") {
            val task1 = Task("first-task", taskRunConfig, group = "Build tasks")
            val task2 = Task("other-task", taskRunConfig, group = "Build tasks")
            val task3 = Task("another-task-with-a-description", taskRunConfig, "do the thing", group = "Build tasks")
            val config = Configuration("the_project", TaskMap(task1, task2, task3), ContainerMap())

            val configLoader by createForEachTest {
                mock<ConfigurationLoader> {
                    on { loadConfig(configFile) } doReturn config
                }
            }

            val output by createForEachTest { ByteArrayOutputStream() }
            val command by createForEachTest { ListTasksCommand(configFile, configLoader, PrintStream(output)) }

            describe("when the configuration file can be loaded") {
                val exitCode by runForEachTest { command.run() }

                it("prints the names of the available tasks in alphabetical order with the group name shown") {
                    assertThat(
                        output.toString(), equalTo(
                            """
                                |Build tasks:
                                |- another-task-with-a-description: do the thing
                                |- first-task
                                |- other-task
                                |
                            """.trimMargin().withPlatformSpecificLineSeparator()
                        )
                    )
                }

                it("returns a zero exit code") {
                    assertThat(exitCode, equalTo(0))
                }
            }
        }

        describe("when invoked with a configuration file with multiple groups defined") {
            val task1 = Task("first-task", taskRunConfig, group = "Test tasks")
            val task2 = Task("other-task", taskRunConfig, group = "Build tasks")
            val task3 = Task("another-task-with-a-description", taskRunConfig, "do the thing", group = "Build tasks")
            val config = Configuration("the_project", TaskMap(task1, task2, task3), ContainerMap())

            val configLoader by createForEachTest {
                mock<ConfigurationLoader> {
                    on { loadConfig(configFile) } doReturn config
                }
            }

            val output by createForEachTest { ByteArrayOutputStream() }
            val command by createForEachTest { ListTasksCommand(configFile, configLoader, PrintStream(output)) }

            describe("when the configuration file can be loaded") {
                val exitCode by runForEachTest { command.run() }

                it("prints tasks grouped by group name, with groups ordered alphabetically") {
                    assertThat(
                        output.toString(), equalTo(
                            """
                                |Build tasks:
                                |- another-task-with-a-description: do the thing
                                |- other-task
                                |
                                |Test tasks:
                                |- first-task
                                |
                            """.trimMargin().withPlatformSpecificLineSeparator()
                        )
                    )
                }

                it("returns a zero exit code") {
                    assertThat(exitCode, equalTo(0))
                }
            }
        }

        describe("when invoked with a configuration file where some tasks do not have a group") {
            val task1 = Task("first-task", taskRunConfig, group = "")
            val task2 = Task("other-task", taskRunConfig, group = "Build tasks")
            val task3 = Task("another-task-with-a-description", taskRunConfig, "do the thing", group = "Build tasks")
            val config = Configuration("the_project", TaskMap(task1, task2, task3), ContainerMap())

            val configLoader by createForEachTest {
                mock<ConfigurationLoader> {
                    on { loadConfig(configFile) } doReturn config
                }
            }

            val output by createForEachTest { ByteArrayOutputStream() }
            val command by createForEachTest { ListTasksCommand(configFile, configLoader, PrintStream(output)) }

            describe("when the configuration file can be loaded") {
                val exitCode by runForEachTest { command.run() }

                it("prints tasks grouped by group name, with the ungrouped tasks appearing last") {
                    assertThat(
                        output.toString(), equalTo(
                            """
                                |Build tasks:
                                |- another-task-with-a-description: do the thing
                                |- other-task
                                |
                                |Ungrouped tasks:
                                |- first-task
                                |
                            """.trimMargin().withPlatformSpecificLineSeparator()
                        )
                    )
                }

                it("returns a zero exit code") {
                    assertThat(exitCode, equalTo(0))
                }
            }
        }
    }
})
