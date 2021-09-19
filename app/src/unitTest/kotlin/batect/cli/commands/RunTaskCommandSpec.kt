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
import batect.config.Container
import batect.config.ContainerMap
import batect.config.PullImage
import batect.config.RawConfiguration
import batect.config.TaskMap
import batect.config.io.ConfigurationLoadResult
import batect.config.io.ConfigurationLoader
import batect.execution.SessionRunner
import batect.ioc.SessionKodeinFactory
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.runForEachTest
import batect.ui.OutputStyle
import batect.updates.UpdateNotifier
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RunTaskCommandSpec : Spek({
    describe("a 'run task' command") {
        describe("when invoked") {
            val fileSystem = Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix())
            val configFile = fileSystem.getPath("config.yml")
            val taskName = "the-task"
            val config = RawConfiguration("the_project", TaskMap(), ContainerMap(Container("the-container", PullImage("the-image"))))

            val baseCommandLineOptions = CommandLineOptions(
                configurationFileName = configFile,
                taskName = taskName
            )

            val configLoader by createForEachTest {
                mock<ConfigurationLoader> {
                    on { loadConfig(configFile) } doReturn ConfigurationLoadResult(config, emptySet())
                }
            }

            val updateNotifier by createForEachTest { mock<UpdateNotifier>() }
            val backgroundTaskManager by createForEachTest { mock<BackgroundTaskManager>() }

            val expectedTaskExitCode = 123
            val sessionRunner by createForEachTest {
                mock<SessionRunner> {
                    on { runTaskAndPrerequisites(taskName) } doReturn expectedTaskExitCode
                }
            }

            val sessionKodeinFactory by createForEachTest {
                mock<SessionKodeinFactory> {
                    on { create(any()) } doReturn DI.direct {
                        bind<SessionRunner>() with instance(sessionRunner)
                    }
                }
            }

            val dockerConnectivity by createForEachTest {
                fakeDockerConnectivity(
                    DI.direct {
                        bind<SessionKodeinFactory>() with instance(sessionKodeinFactory)
                    }
                )
            }

            given("quiet output mode is not being used") {
                val commandLineOptions = baseCommandLineOptions.copy(requestedOutputStyle = OutputStyle.Fancy)
                val command by createForEachTest { RunTaskCommand(commandLineOptions, configLoader, updateNotifier, backgroundTaskManager, dockerConnectivity) }
                val exitCode by runForEachTest { command.run() }

                it("runs the task") {
                    verify(sessionRunner).runTaskAndPrerequisites(taskName)
                }

                it("returns the exit code of the task") {
                    assertThat(exitCode, equalTo(expectedTaskExitCode))
                }

                it("displays any update notifications before running the task") {
                    inOrder(sessionRunner, updateNotifier) {
                        verify(updateNotifier).run()
                        verify(sessionRunner).runTaskAndPrerequisites(any())
                    }
                }

                it("triggers background tasks after loading the config but before running the task") {
                    inOrder(configLoader, backgroundTaskManager, sessionRunner) {
                        verify(configLoader).loadConfig(any(), anyOrNull())
                        verify(backgroundTaskManager).startBackgroundTasks()
                        verify(sessionRunner).runTaskAndPrerequisites(any())
                    }
                }

                it("creates the session Kodein context with the raw configuration") {
                    verify(sessionKodeinFactory).create(config)
                }
            }

            given("quiet output mode is being used") {
                val commandLineOptions = baseCommandLineOptions.copy(requestedOutputStyle = OutputStyle.Quiet)
                val command by createForEachTest { RunTaskCommand(commandLineOptions, configLoader, updateNotifier, backgroundTaskManager, dockerConnectivity) }
                beforeEachTest { command.run() }

                it("does not display any update notifications") {
                    verify(updateNotifier, never()).run()
                }
            }
        }
    }
})
