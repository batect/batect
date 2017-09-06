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

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import batect.PrintStreamType
import batect.cli.Command
import batect.cli.CommonOptions
import batect.cli.Succeeded
import batect.config.Configuration
import batect.config.ContainerMap
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.config.io.ConfigurationLoader
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object ListTasksCommandSpec : Spek({
    describe("a 'list tasks' command") {
        describe("command line interface") {
            val commandLine = ListTasksCommandDefinition()
            val configLoader = mock<ConfigurationLoader>()
            val outputStream = mock<PrintStream>()

            val kodein = Kodein {
                bind<ConfigurationLoader>() with instance(configLoader)
                bind<PrintStream>(PrintStreamType.Error) with instance(outputStream)
                bind<String>(CommonOptions.ConfigurationFileName) with instance("thefile.yml")
            }

            describe("when given no parameters") {
                val result = commandLine.parse(emptyList(), kodein)

                it("indicates that parsing succeeded") {
                    assertThat(result, isA<Succeeded>())
                }

                it("returns a command instance ready for use") {
                    assertThat((result as Succeeded).command, equalTo<Command>(ListTasksCommand("thefile.yml", configLoader, outputStream)))
                }
            }
        }

        describe("when invoked") {
            val configFile = "config.yml"
            val taskRunConfig = TaskRunConfiguration("some-container", "dont-care")
            val task1 = Task("first-task", taskRunConfig)
            val task2 = Task("other-task", taskRunConfig)
            val task3 = Task("another-task-with-a-description", taskRunConfig, "do the thing")
            val config = Configuration("the_project", TaskMap(task1, task2, task3), ContainerMap())

            val configLoader = mock<ConfigurationLoader> {
                on { loadConfig(configFile) } doReturn config
            }

            val output = ByteArrayOutputStream()
            val command = ListTasksCommand(configFile, configLoader, PrintStream(output))

            describe("when the configuration file can be loaded") {
                val exitCode = command.run()

                it("prints the names of the available tasks in alphabetical order") {
                    assertThat(output.toString(), equalTo("""
                        |Available tasks:
                        |- another-task-with-a-description: do the thing
                        |- first-task
                        |- other-task
                        |""".trimMargin()))
                }

                it("returns a zero exit code") {
                    assertThat(exitCode, equalTo(0))
                }
            }
        }
    }
})
