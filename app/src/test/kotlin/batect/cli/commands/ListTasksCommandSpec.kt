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

package batect.cli.commands

import batect.config.Configuration
import batect.config.ContainerMap
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.config.io.ConfigurationLoader
import batect.os.Command
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object ListTasksCommandSpec : Spek({
    describe("a 'list tasks' command") {
        describe("when invoked") {
            val fileSystem = Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix())
            val configFile = fileSystem.getPath("config.yml")
            val taskRunConfig = TaskRunConfiguration("some-container", Command.parse("dont-care"))
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
