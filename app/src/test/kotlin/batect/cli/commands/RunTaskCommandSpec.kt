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

import batect.PrintStreamType
import batect.TaskRunner
import batect.cli.CommandLineParsingResult
import batect.cli.CommonOptions
import batect.cli.options.LevelOfParallelismDefaultValueProvider
import batect.config.Configuration
import batect.config.ContainerMap
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.config.io.ConfigurationLoader
import batect.logging.Logger
import batect.logging.LoggerFactory
import batect.logging.Severity
import batect.model.BehaviourAfterFailure
import batect.model.RunOptions
import batect.model.TaskExecutionOrderResolutionException
import batect.model.TaskExecutionOrderResolver
import batect.testutils.InMemoryLogSink
import batect.testutils.createForEachTest
import batect.testutils.hasMessage
import batect.testutils.withException
import batect.testutils.withSeverity
import batect.ui.Console
import batect.ui.ConsoleColor
import batect.updates.UpdateNotifier
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.atLeastOnce
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object RunTaskCommandSpec : Spek({
    describe("a 'run task' command") {
        describe("command line interface") {
            val commandLine by createForEachTest { RunTaskCommandDefinition() }
            val configLoader = mock<ConfigurationLoader>()
            val taskRunner = mock<TaskRunner>()
            val taskExecutionOrderResolver = mock<TaskExecutionOrderResolver>()
            val updateNotifier = mock<UpdateNotifier>()
            val console = mock<Console>()
            val errorConsole = mock<Console>()
            val logger = mock<Logger>()
            val loggerFactory = mock<LoggerFactory> {
                on { createLoggerForClass(RunTaskCommand::class) } doReturn logger
            }

            val kodein = Kodein {
                bind<ConfigurationLoader>() with instance(configLoader)
                bind<TaskRunner>() with instance(taskRunner)
                bind<TaskExecutionOrderResolver>() with instance(taskExecutionOrderResolver)
                bind<String>(CommonOptions.ConfigurationFileName) with instance("thefile.yml")
                bind<UpdateNotifier>() with instance(updateNotifier)
                bind<Console>(PrintStreamType.Output) with instance(console)
                bind<Console>(PrintStreamType.Error) with instance(errorConsole)
                bind<LoggerFactory>() with instance(loggerFactory)
            }

            on("when given one parameter") {
                val result = commandLine.parse(listOf("the-task"), kodein)

                it("indicates that parsing succeeded") {
                    assertThat(result, isA<CommandLineParsingResult.Succeeded>())
                }

                it("returns a command instance ready for use") {
                    val runOptions = RunOptions(LevelOfParallelismDefaultValueProvider.value, BehaviourAfterFailure.Cleanup)

                    assertThat((result as CommandLineParsingResult.Succeeded).command, equalTo<Command>(
                        RunTaskCommand("thefile.yml", "the-task", runOptions, configLoader, taskExecutionOrderResolver, taskRunner, updateNotifier, console, errorConsole, logger)))
                }
            }

            on("when given one parameter and a level of parallelism") {
                val result = commandLine.parse(listOf("--level-of-parallelism", "123", "the-task"), kodein)

                it("indicates that parsing succeeded") {
                    assertThat(result, isA<CommandLineParsingResult.Succeeded>())
                }

                it("returns a command instance ready for use with the desired level of parallelism") {
                    val runOptions = RunOptions(123, BehaviourAfterFailure.Cleanup)

                    assertThat((result as CommandLineParsingResult.Succeeded).command, equalTo<Command>(
                        RunTaskCommand("thefile.yml", "the-task", runOptions, configLoader, taskExecutionOrderResolver, taskRunner, updateNotifier, console, errorConsole, logger)))
                }
            }

            on("when given one parameter and a flag to disable removing containers after a failure") {
                val result = commandLine.parse(listOf("--no-cleanup-after-failure", "the-task"), kodein)

                it("indicates that parsing succeeded") {
                    assertThat(result, isA<CommandLineParsingResult.Succeeded>())
                }

                it("returns a command instance ready for use with the desired cleanup mode") {
                    val runOptions = RunOptions(LevelOfParallelismDefaultValueProvider.value, BehaviourAfterFailure.DontCleanup)

                    assertThat((result as CommandLineParsingResult.Succeeded).command, equalTo<Command>(
                        RunTaskCommand("thefile.yml", "the-task", runOptions, configLoader, taskExecutionOrderResolver, taskRunner, updateNotifier, console, errorConsole, logger)))
                }
            }
        }

        describe("when invoked") {
            val configFile = "config.yml"
            val taskName = "the-task"
            val mainTask = Task(taskName, TaskRunConfiguration("the-container"))
            val config = Configuration("the_project", TaskMap(), ContainerMap())
            val expectedTaskExitCode = 123
            val runOptions = RunOptions(64, BehaviourAfterFailure.Cleanup)
            val logSink = InMemoryLogSink()
            val logger = Logger("test.source", logSink)

            val configLoader = mock<ConfigurationLoader> {
                on { loadConfig(configFile) } doReturn config
            }

            val updateNotifier by createForEachTest { mock<UpdateNotifier>() }
            val console by createForEachTest { mock<Console>() }
            val redErrorConsole by createForEachTest { mock<Console>() }
            val errorConsole by createForEachTest {
                mock<Console> {
                    on { withColor(eq(ConsoleColor.Red), any()) } doAnswer {
                        val printStatements = it.getArgument<Console.() -> Unit>(1)
                        printStatements(redErrorConsole)
                    }
                }
            }

            describe("when the configuration file can be loaded and the task has no dependencies") {
                val taskExecutionOrderResolver = mock<TaskExecutionOrderResolver> {
                    on { resolveExecutionOrder(config, taskName) } doReturn listOf(mainTask)
                }

                on("and that task returns a non-zero exit code") {
                    val taskRunner = mock<TaskRunner> {
                        on { run(config, mainTask, runOptions) } doReturn expectedTaskExitCode
                    }

                    val command = RunTaskCommand(configFile, taskName, runOptions, configLoader, taskExecutionOrderResolver, taskRunner, updateNotifier, console, errorConsole, logger)
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
                }

                on("and that task returns a zero exit code") {
                    val taskRunner = mock<TaskRunner> {
                        on { run(config, mainTask, runOptions) } doReturn 0
                    }

                    val command = RunTaskCommand(configFile, taskName, runOptions, configLoader, taskExecutionOrderResolver, taskRunner, updateNotifier, console, errorConsole, logger)
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
                }
            }

            describe("when the configuration file can be loaded and the task has a dependency") {
                val otherTask = Task("other-task", TaskRunConfiguration("the-other-container"))

                val taskExecutionOrderResolver = mock<TaskExecutionOrderResolver> {
                    on { resolveExecutionOrder(config, taskName) } doReturn listOf(otherTask, mainTask)
                }

                on("and the dependency finishes with an exit code of 0") {
                    val taskRunner = mock<TaskRunner> {
                        on { run(config, otherTask, runOptions) } doReturn 0
                        on { run(config, mainTask, runOptions) } doReturn expectedTaskExitCode
                    }

                    val command = RunTaskCommand(configFile, taskName, runOptions, configLoader, taskExecutionOrderResolver, taskRunner, updateNotifier, console, errorConsole, logger)
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
                }

                on("and the dependency finishes with a non-zero exit code") {
                    val taskRunner = mock<TaskRunner> {
                        on { run(config, otherTask, runOptions) } doReturn 1
                    }

                    val command = RunTaskCommand(configFile, taskName, runOptions, configLoader, taskExecutionOrderResolver, taskRunner, updateNotifier, console, errorConsole, logger)
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
                }
            }

            on("when the determining the task execution order fails") {
                val exception = TaskExecutionOrderResolutionException("Something went wrong.")
                val taskExecutionOrderResolver = mock<TaskExecutionOrderResolver> {
                    on { resolveExecutionOrder(config, taskName) } doThrow exception
                }

                val taskRunner = mock<TaskRunner>()

                val command = RunTaskCommand(configFile, taskName, runOptions, configLoader, taskExecutionOrderResolver, taskRunner, updateNotifier, console, errorConsole, logger)
                val exitCode = command.run()

                it("prints a message to the output") {
                    verify(redErrorConsole).println("Something went wrong.")
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
})
