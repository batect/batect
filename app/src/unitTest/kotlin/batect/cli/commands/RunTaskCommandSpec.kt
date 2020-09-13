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

import batect.cli.CommandLineOptions
import batect.config.Configuration
import batect.config.Container
import batect.config.ContainerMap
import batect.config.PullImage
import batect.config.TaskMap
import batect.config.io.ConfigurationLoader
import batect.execution.CleanupOption
import batect.execution.RunOptions
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
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RunTaskCommandSpec : Spek({
    describe("a 'run task' command") {
        describe("when invoked") {
            val fileSystem = Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix())
            val configFile = fileSystem.getPath("config.yml")
            val taskName = "the-task"
            val config = Configuration("the_project", TaskMap(), ContainerMap(Container("the-container", PullImage("the-image"))))
            val configWithImageOverrides = Configuration("the_project", TaskMap(), ContainerMap(Container("the-container", PullImage("the-new-image"))))
            val imageOverrides = mapOf("the-container" to "the-new-image")
            val commandLineOptions = CommandLineOptions(imageOverrides = imageOverrides)
            val runOptions = RunOptions(taskName, emptyList(), CleanupOption.DontCleanup, CleanupOption.Cleanup)

            val configLoader by createForEachTest {
                mock<ConfigurationLoader> {
                    on { loadConfig(configFile) } doReturn config
                }
            }

            val updateNotifier by createForEachTest { mock<UpdateNotifier>() }
            val backgroundTaskManager by createForEachTest { mock<BackgroundTaskManager>() }

            val expectedTaskExitCode = 123
            val sessionRunner by createForEachTest { mock<SessionRunner> {
                on { runTaskAndPrerequisites(taskName) } doReturn expectedTaskExitCode
            } }

            val sessionKodeinFactory by createForEachTest {
                mock<SessionKodeinFactory> {
                    on { create(configWithImageOverrides) } doReturn DI.direct {
                        bind<SessionRunner>() with instance(sessionRunner)
                    }
                }
            }

            val dockerConnectivity by createForEachTest {
                fakeDockerConnectivity(DI.direct {
                    bind<SessionKodeinFactory>() with instance(sessionKodeinFactory)
                })
            }

            given("quiet output mode is not being used") {
                val outputMode = OutputStyle.Fancy
                val command by createForEachTest { RunTaskCommand(configFile, commandLineOptions, runOptions, configLoader, updateNotifier, backgroundTaskManager, dockerConnectivity, outputMode) }
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
                        verify(configLoader).loadConfig(any())
                        verify(backgroundTaskManager).startBackgroundTasks()
                        verify(sessionRunner).runTaskAndPrerequisites(any())
                    }
                }
            }

            given("quiet output mode is being used") {
                val outputMode = OutputStyle.Quiet
                val command by createForEachTest { RunTaskCommand(configFile, commandLineOptions, runOptions, configLoader, updateNotifier, backgroundTaskManager, dockerConnectivity, outputMode) }
                beforeEachTest { command.run() }

                it("does not display any update notifications") {
                    verify(updateNotifier, never()).run()
                }
            }
        }
    }
})
