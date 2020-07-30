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
import batect.config.Container
import batect.config.ContainerMap
import batect.config.PullImage
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.config.includes.GitRepositoryCacheCleanupTask
import batect.config.io.ConfigurationLoader
import batect.execution.CleanupOption
import batect.execution.RunOptions
import batect.execution.TaskExecutionOrderResolutionException
import batect.execution.TaskExecutionOrderResolver
import batect.execution.TaskRunner
import batect.ioc.SessionKodeinFactory
import batect.logging.Logger
import batect.logging.Severity
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.logging.InMemoryLogSink
import batect.testutils.logging.hasMessage
import batect.testutils.logging.withException
import batect.testutils.logging.withSeverity
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.ui.Console
import batect.ui.OutputStyle
import batect.ui.text.Text
import batect.updates.UpdateNotifier
import batect.wrapper.WrapperCacheCleanupTask
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RunTaskCommandSpec : Spek({
    describe("a 'run task' command") {
        describe("when invoked") {
            val fileSystem = Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix())
            val configFile = fileSystem.getPath("config.yml")
            val taskName = "the-task"
            val mainTask = Task(taskName, TaskRunConfiguration("the-container"))
            val config = Configuration("the_project", TaskMap(), ContainerMap(Container("the-container", PullImage("the-image"))))
            val configWithImageOverrides = Configuration("the_project", TaskMap(), ContainerMap(Container("the-container", PullImage("the-new-image"))))
            val expectedTaskExitCode = 123
            val imageOverrides = mapOf("the-container" to "the-new-image")
            val runOptions = RunOptions(taskName, emptyList(), CleanupOption.DontCleanup, CleanupOption.Cleanup, true, imageOverrides)
            val logSink = InMemoryLogSink()
            val logger = Logger("test.source", logSink)

            val configLoader by createForEachTest {
                mock<ConfigurationLoader> {
                    on { loadConfig(configFile) } doReturn config
                }
            }

            val updateNotifier by createForEachTest { mock<UpdateNotifier>() }
            val wrapperCacheCleanupTask by createForEachTest { mock<WrapperCacheCleanupTask>() }
            val gitRepositoryCacheCleanupTask by createForEachTest { mock<GitRepositoryCacheCleanupTask>() }
            val console by createForEachTest { mock<Console>() }
            val errorConsole by createForEachTest { mock<Console>() }
            val taskRunner by createForEachTest { mock<TaskRunner>() }
            val sessionKodeinFactory by createForEachTest {
                mock<SessionKodeinFactory> {
                    on { create(configWithImageOverrides) } doReturn Kodein.direct {
                        bind<TaskRunner>() with instance(taskRunner)
                    }
                }
            }

            val dockerConnectivity by createForEachTest {
                fakeDockerConnectivity(Kodein.direct {
                    bind<SessionKodeinFactory>() with instance(sessionKodeinFactory)
                })
            }

            given("the configuration file can be loaded") {
                given("the task has no prerequisites") {
                    val taskExecutionOrderResolver = mock<TaskExecutionOrderResolver> {
                        on { resolveExecutionOrder(configWithImageOverrides, taskName) } doReturn listOf(mainTask)
                    }

                    given("that task returns a zero exit code") {
                        beforeEachTest {
                            whenever(taskRunner.run(mainTask, runOptions)).thenReturn(0)
                        }

                        given("quiet output mode is not being used") {
                            val outputMode = OutputStyle.Fancy
                            val command by createForEachTest { RunTaskCommand(configFile, runOptions, configLoader, taskExecutionOrderResolver, updateNotifier, wrapperCacheCleanupTask, gitRepositoryCacheCleanupTask, dockerConnectivity, outputMode, console, errorConsole, logger) }
                            val exitCode by runForEachTest { command.run() }

                            it("runs the task") {
                                verify(taskRunner).run(mainTask, runOptions)
                            }

                            it("returns the exit code of the task") {
                                assertThat(exitCode, equalTo(0))
                            }

                            it("does not print any blank lines after the task") {
                                verify(console, never()).println()
                            }

                            it("displays any update notifications before running the task") {
                                inOrder(taskRunner, updateNotifier) {
                                    verify(updateNotifier).run()
                                    verify(taskRunner).run(any(), any())
                                }
                            }

                            it("triggers wrapper cache cleanup before running the task") {
                                inOrder(wrapperCacheCleanupTask, taskRunner) {
                                    verify(wrapperCacheCleanupTask).start()
                                    verify(taskRunner).run(any(), any())
                                }
                            }

                            it("triggers Git repository cache cleanup after loading the config but before running the task") {
                                inOrder(configLoader, gitRepositoryCacheCleanupTask, taskRunner) {
                                    verify(configLoader).loadConfig(any())
                                    verify(gitRepositoryCacheCleanupTask).start()
                                    verify(taskRunner).run(any(), any())
                                }
                            }

                            it("does not print anything to the error console") {
                                verifyZeroInteractions(errorConsole)
                            }
                        }

                        given("quiet output mode is being used") {
                            val outputMode = OutputStyle.Quiet
                            val command by createForEachTest { RunTaskCommand(configFile, runOptions, configLoader, taskExecutionOrderResolver, updateNotifier, wrapperCacheCleanupTask, gitRepositoryCacheCleanupTask, dockerConnectivity, outputMode, console, errorConsole, logger) }
                            beforeEachTest { command.run() }

                            it("does not display any update notifications") {
                                verify(updateNotifier, never()).run()
                            }
                        }
                    }

                    given("that task returns a non-zero exit code") {
                        beforeEachTest {
                            whenever(taskRunner.run(mainTask, runOptions)).thenReturn(expectedTaskExitCode)
                        }

                        val command by createForEachTest { RunTaskCommand(configFile, runOptions, configLoader, taskExecutionOrderResolver, updateNotifier, wrapperCacheCleanupTask, gitRepositoryCacheCleanupTask, dockerConnectivity, null, console, errorConsole, logger) }
                        val exitCode by runForEachTest { command.run() }

                        it("runs the task") {
                            verify(taskRunner).run(mainTask, runOptions)
                        }

                        it("returns the exit code of the task") {
                            assertThat(exitCode, equalTo(expectedTaskExitCode))
                        }

                        it("does not print any blank lines after the task") {
                            verify(console, never()).println()
                        }

                        it("displays any update notifications before running the task") {
                            inOrder(taskRunner, updateNotifier) {
                                verify(updateNotifier).run()
                                verify(taskRunner).run(any(), any())
                            }
                        }

                        it("triggers wrapper cache cleanup before running the task") {
                            inOrder(wrapperCacheCleanupTask, taskRunner) {
                                verify(wrapperCacheCleanupTask).start()
                                verify(taskRunner).run(any(), any())
                            }
                        }

                        it("triggers Git repository cache cleanup after loading the config but before running the task") {
                            inOrder(configLoader, gitRepositoryCacheCleanupTask, taskRunner) {
                                verify(configLoader).loadConfig(any())
                                verify(gitRepositoryCacheCleanupTask).start()
                                verify(taskRunner).run(any(), any())
                            }
                        }

                        it("does not print anything to the error console") {
                            verifyZeroInteractions(errorConsole)
                        }
                    }
                }

                given("the task has a prerequisite") {
                    val otherTask = Task("other-task", TaskRunConfiguration("the-other-container"))
                    val runOptionsForOtherTask = runOptions.copy(behaviourAfterSuccess = CleanupOption.Cleanup)

                    val taskExecutionOrderResolver = mock<TaskExecutionOrderResolver> {
                        on { resolveExecutionOrder(configWithImageOverrides, taskName) } doReturn listOf(otherTask, mainTask)
                    }

                    given("the dependency finishes with an exit code of 0") {
                        beforeEachTest {
                            whenever(taskRunner.run(otherTask, runOptionsForOtherTask)).thenReturn(0)
                            whenever(taskRunner.run(mainTask, runOptions)).thenReturn(expectedTaskExitCode)
                        }

                        given("quiet output mode is not being used") {
                            val outputStyle = OutputStyle.Fancy
                            val command by createForEachTest { RunTaskCommand(configFile, runOptions, configLoader, taskExecutionOrderResolver, updateNotifier, wrapperCacheCleanupTask, gitRepositoryCacheCleanupTask, dockerConnectivity, outputStyle, console, errorConsole, logger) }
                            val exitCode by runForEachTest { command.run() }

                            it("runs the dependency task with cleanup on success enabled") {
                                verify(taskRunner).run(otherTask, runOptionsForOtherTask)
                            }

                            it("runs the main task with cleanup on success matching the preference provided by the user") {
                                verify(taskRunner).run(mainTask, runOptions)
                            }

                            it("runs the dependency before the main task, and prints a blank line in between") {
                                inOrder(taskRunner, console) {
                                    verify(taskRunner).run(otherTask, runOptionsForOtherTask)
                                    verify(console).println()
                                    verify(taskRunner).run(mainTask, runOptions)
                                }
                            }

                            it("returns the exit code of the main task") {
                                assertThat(exitCode, equalTo(expectedTaskExitCode))
                            }

                            it("displays any update notifications before running the task") {
                                inOrder(taskRunner, updateNotifier) {
                                    verify(updateNotifier).run()
                                    verify(taskRunner, atLeastOnce()).run(any(), any())
                                }
                            }

                            it("triggers wrapper cache cleanup before running the task") {
                                inOrder(wrapperCacheCleanupTask, taskRunner) {
                                    verify(wrapperCacheCleanupTask).start()
                                    verify(taskRunner, atLeastOnce()).run(any(), any())
                                }
                            }

                            it("triggers Git repository cache cleanup after loading the config but before running the task") {
                                inOrder(configLoader, gitRepositoryCacheCleanupTask, taskRunner) {
                                    verify(configLoader).loadConfig(any())
                                    verify(gitRepositoryCacheCleanupTask).start()
                                    verify(taskRunner, atLeastOnce()).run(any(), any())
                                }
                            }

                            it("does not print anything to the error console") {
                                verifyZeroInteractions(errorConsole)
                            }
                        }

                        given("quiet output mode is being used") {
                            val outputStyle = OutputStyle.Quiet
                            val command by createForEachTest { RunTaskCommand(configFile, runOptions, configLoader, taskExecutionOrderResolver, updateNotifier, wrapperCacheCleanupTask, gitRepositoryCacheCleanupTask, dockerConnectivity, outputStyle, console, errorConsole, logger) }
                            beforeEachTest { command.run() }

                            it("runs the dependency before the main task, and does not print a blank line in between") {
                                inOrder(taskRunner, console) {
                                    verify(taskRunner).run(otherTask, runOptionsForOtherTask)
                                    verify(console, never()).println()
                                    verify(taskRunner).run(mainTask, runOptions)
                                }
                            }
                        }
                    }

                    on("and the dependency finishes with a non-zero exit code") {
                        beforeEachTest {
                            whenever(taskRunner.run(otherTask, runOptionsForOtherTask)).thenReturn(1)
                        }

                        val command by createForEachTest { RunTaskCommand(configFile, runOptions, configLoader, taskExecutionOrderResolver, updateNotifier, wrapperCacheCleanupTask, gitRepositoryCacheCleanupTask, dockerConnectivity, null, console, errorConsole, logger) }
                        val exitCode by runForEachTest { command.run() }

                        it("runs the dependency task") {
                            verify(taskRunner).run(otherTask, runOptionsForOtherTask)
                        }

                        it("does not run the main task") {
                            verify(taskRunner, never()).run(mainTask, runOptions)
                        }

                        it("returns the exit code of the dependency task") {
                            assertThat(exitCode, equalTo(1))
                        }

                        it("displays any update notifications before running the task") {
                            inOrder(taskRunner, updateNotifier) {
                                verify(updateNotifier).run()
                                verify(taskRunner).run(any(), any())
                            }
                        }

                        it("triggers wrapper cache cleanup before running the task") {
                            inOrder(wrapperCacheCleanupTask, taskRunner) {
                                verify(wrapperCacheCleanupTask).start()
                                verify(taskRunner).run(any(), any())
                            }
                        }

                        it("triggers Git repository cache cleanup after loading the config but before running the task") {
                            inOrder(configLoader, gitRepositoryCacheCleanupTask, taskRunner) {
                                verify(configLoader).loadConfig(any())
                                verify(gitRepositoryCacheCleanupTask).start()
                                verify(taskRunner).run(any(), any())
                            }
                        }

                        it("does not print anything to the error console") {
                            verifyZeroInteractions(errorConsole)
                        }
                    }
                }

                on("when determining the task execution order fails") {
                    val exception = TaskExecutionOrderResolutionException("Something went wrong.")
                    val taskExecutionOrderResolver = mock<TaskExecutionOrderResolver> {
                        on { resolveExecutionOrder(configWithImageOverrides, taskName) } doThrow exception
                    }

                    val command by createForEachTest { RunTaskCommand(configFile, runOptions, configLoader, taskExecutionOrderResolver, updateNotifier, wrapperCacheCleanupTask, gitRepositoryCacheCleanupTask, dockerConnectivity, null, console, errorConsole, logger) }
                    val exitCode by runForEachTest { command.run() }

                    it("prints a message to the output") {
                        verify(errorConsole).println(Text.red("Something went wrong."))
                    }

                    it("returns a non-zero exit code") {
                        assertThat(exitCode, !equalTo(0))
                    }

                    it("logs a message with the exception") {
                        assertThat(logSink, hasMessage(withSeverity(Severity.Error) and withException(exception)))
                    }
                }
            }
        }
    }
})
