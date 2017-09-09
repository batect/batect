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
import com.nhaarman.mockito_kotlin.verify
import batect.TaskRunner
import batect.cli.CommonOptions
import batect.cli.Succeeded
import batect.config.Configuration
import batect.config.ContainerMap
import batect.config.TaskMap
import batect.config.io.ConfigurationLoader
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

object RunTaskCommandSpec : Spek({
    describe("a 'run task' command") {
        describe("command line interface") {
            val commandLine = RunTaskCommandDefinition()
            val configLoader = mock<ConfigurationLoader>()
            val taskRunner = mock<TaskRunner>()

            val kodein = Kodein {
                bind<ConfigurationLoader>() with instance(configLoader)
                bind<TaskRunner>() with instance(taskRunner)
                bind<String>(CommonOptions.ConfigurationFileName) with instance("thefile.yml")
            }

            describe("when given one parameter") {
                val result = commandLine.parse(listOf("the-task"), kodein)

                it("indicates that parsing succeeded") {
                    assertThat(result, isA<Succeeded>())
                }

                it("returns a command instance ready for use") {
                    assertThat((result as Succeeded).command, equalTo<Command>(RunTaskCommand("thefile.yml", "the-task", configLoader, taskRunner)))
                }
            }
        }

        describe("when invoked") {
            val configFile = "config.yml"
            val taskName = "the_task"
            val config = Configuration("the_project", TaskMap(), ContainerMap())
            val expectedTaskExitCode = 123

            val configLoader = mock<ConfigurationLoader> {
                on { loadConfig(configFile) } doReturn config
            }

            val taskRunner = mock<TaskRunner> {
                on { run(config, taskName) } doReturn expectedTaskExitCode
            }

            val command = RunTaskCommand(configFile, taskName, configLoader, taskRunner)

            describe("when the configuration file can be loaded and the task runs successfully") {
                val exitCode = command.run()

                it("runs the task") {
                    verify(taskRunner).run(config, taskName)
                }

                it("returns the exit code of the task") {
                    assertThat(exitCode, equalTo(expectedTaskExitCode))
                }
            }
        }
    }
})
