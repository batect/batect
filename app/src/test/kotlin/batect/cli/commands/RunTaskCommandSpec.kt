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
import batect.docker.DockerClient
import batect.docker.DockerConnectivityCheckResult
import batect.execution.BehaviourAfterFailure
import batect.execution.RunOptions
import batect.execution.TaskExecutionOrderResolutionException
import batect.execution.TaskExecutionOrderResolver
import batect.execution.TaskRunner
import batect.logging.Logger
import batect.logging.Severity
import batect.testutils.InMemoryLogSink
import batect.testutils.createForEachTest
import batect.testutils.hasMessage
import batect.testutils.withException
import batect.testutils.withSeverity
import batect.ui.Console
import batect.ui.text.Text
import batect.updates.UpdateNotifier
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
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object RunTaskCommandSpec : Spek({
    describe("a 'run task' command") {
        describe("when invoked") {
            val fileSystem = Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix())
            val configFile = fileSystem.getPath("config.yml")
            val taskName = "the-task"
            val mainTask = Task(taskName, TaskRunConfiguration("the-container"))
            val config = Configuration("the_project", TaskMap(), ContainerMap())
            val expectedTaskExitCode = 123
            val runOptions = RunOptions(taskName, emptyList(), 64, BehaviourAfterFailure.Cleanup, true)
            val logSink = InMemoryLogSink()
            val logger = Logger("test.source", logSink)

            val configLoader = mock<ConfigurationLoader> {
                on { loadConfig(configFile) } doReturn config
            }

            val updateNotifier by createForEachTest { mock<UpdateNotifier>() }
            val console by createForEachTest { mock<Console>() }
            val errorConsole by createForEachTest { mock<Console>() }

            given("configuration file can be loaded") {
                given("Docker is available") {
                    val dockerClient = mock<DockerClient> {
                        on { checkConnectivity() } doReturn DockerConnectivityCheckResult.Succeeded
                    }

                    given("the task has no dependencies") {
                        val taskExecutionOrderResolver = mock<TaskExecutionOrderResolver> {
                            on { resolveExecutionOrder(config, taskName) } doReturn listOf(mainTask)
                        }

                        on("and that task returns a non-zero exit code") {
                            val taskRunner = mock<TaskRunner> {
                                on { run(config, mainTask, runOptions) } doReturn expectedTaskExitCode
                            }

                            val command = RunTaskCommand(configFile, runOptions, configLoader, taskExecutionOrderResolver, taskRunner, updateNotifier, dockerClient, console, errorConsole, logger)
                            val exitCode = command.run()

                            it("runs the task") {
                                verify(taskRunner).run(config, mainTask, runOptions)
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
                                    verify(taskRunner).run(any(), any(), any())
                                }
                            }

                            it("does not print anything to the error console") {
                                verifyZeroInteractions(errorConsole)
                            }
                        }

                        on("and that task returns a zero exit code") {
                            val taskRunner = mock<TaskRunner> {
                                on { run(config, mainTask, runOptions) } doReturn 0
                            }

                            val command = RunTaskCommand(configFile, runOptions, configLoader, taskExecutionOrderResolver, taskRunner, updateNotifier, dockerClient, console, errorConsole, logger)
                            val exitCode = command.run()

                            it("runs the task") {
                                verify(taskRunner).run(config, mainTask, runOptions)
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
                                    verify(taskRunner).run(any(), any(), any())
                                }
                            }

                            it("does not print anything to the error console") {
                                verifyZeroInteractions(errorConsole)
                            }
                        }
                    }

                    given("the task has a dependency") {
                        val otherTask = Task("other-task", TaskRunConfiguration("the-other-container"))

                        val taskExecutionOrderResolver = mock<TaskExecutionOrderResolver> {
                            on { resolveExecutionOrder(config, taskName) } doReturn listOf(otherTask, mainTask)
                        }

                        on("and the dependency finishes with an exit code of 0") {
                            val taskRunner = mock<TaskRunner> {
                                on { run(config, otherTask, runOptions) } doReturn 0
                                on { run(config, mainTask, runOptions) } doReturn expectedTaskExitCode
                            }

                            val command = RunTaskCommand(configFile, runOptions, configLoader, taskExecutionOrderResolver, taskRunner, updateNotifier, dockerClient, console, errorConsole, logger)
                            val exitCode = command.run()

                            it("runs the dependency task") {
                                verify(taskRunner).run(config, otherTask, runOptions)
                            }

                            it("runs the main task") {
                                verify(taskRunner).run(config, mainTask, runOptions)
                            }

                            it("runs the dependency before the main task, and prints a blank line in between") {
                                inOrder(taskRunner, console) {
                                    verify(taskRunner).run(config, otherTask, runOptions)
                                    verify(console).println()
                                    verify(taskRunner).run(config, mainTask, runOptions)
                                }
                            }

                            it("returns the exit code of the main task") {
                                assertThat(exitCode, equalTo(expectedTaskExitCode))
                            }

                            it("displays any update notifications before running the task") {
                                inOrder(taskRunner, updateNotifier) {
                                    verify(updateNotifier).run()
                                    verify(taskRunner, atLeastOnce()).run(any(), any(), any())
                                }
                            }

                            it("does not print anything to the error console") {
                                verifyZeroInteractions(errorConsole)
                            }
                        }

                        on("and the dependency finishes with a non-zero exit code") {
                            val taskRunner = mock<TaskRunner> {
                                on { run(config, otherTask, runOptions) } doReturn 1
                            }

                            val command = RunTaskCommand(configFile, runOptions, configLoader, taskExecutionOrderResolver, taskRunner, updateNotifier, dockerClient, console, errorConsole, logger)
                            val exitCode = command.run()

                            it("runs the dependency task") {
                                verify(taskRunner).run(config, otherTask, runOptions)
                            }

                            it("does not run the main task") {
                                verify(taskRunner, never()).run(config, mainTask, runOptions)
                            }

                            it("returns the exit code of the dependency task") {
                                assertThat(exitCode, equalTo(1))
                            }

                            it("displays any update notifications before running the task") {
                                inOrder(taskRunner, updateNotifier) {
                                    verify(updateNotifier).run()
                                    verify(taskRunner).run(any(), any(), any())
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
                            on { resolveExecutionOrder(config, taskName) } doThrow exception
                        }

                        val taskRunner = mock<TaskRunner>()

                        val command = RunTaskCommand(configFile, runOptions, configLoader, taskExecutionOrderResolver, taskRunner, updateNotifier, dockerClient, console, errorConsole, logger)
                        val exitCode = command.run()

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

                on("when Docker is not available") {
                    val taskExecutionOrderResolver = mock<TaskExecutionOrderResolver>()
                    val taskRunner = mock<TaskRunner>()
                    val dockerClient = mock<DockerClient> {
                        on { checkConnectivity() } doReturn DockerConnectivityCheckResult.Failed("Something went wrong.")
                    }

                    val command = RunTaskCommand(configFile, runOptions, configLoader, taskExecutionOrderResolver, taskRunner, updateNotifier, dockerClient, console, errorConsole, logger)
                    val exitCode = command.run()

                    it("prints a message to the output") {
                        verify(errorConsole).println(Text.red("Docker is not installed, or not available: Something went wrong."))
                    }

                    it("returns a non-zero exit code") {
                        assertThat(exitCode, !equalTo(0))
                    }
                }
            }
        }
    }
})
