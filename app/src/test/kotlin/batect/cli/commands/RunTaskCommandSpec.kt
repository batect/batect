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
import batect.cli.CommonOptions
import batect.cli.Succeeded
import batect.cli.options.LevelOfParallelismDefaultValueProvider
import batect.config.Configuration
import batect.config.ContainerMap
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.config.io.ConfigurationLoader
import batect.model.TaskExecutionOrderResolutionException
import batect.model.TaskExecutionOrderResolver
import batect.testutils.CreateForEachTest
import batect.ui.Console
import batect.ui.ConsoleColor
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import com.nhaarman.mockito_kotlin.any
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
            val commandLine = RunTaskCommandDefinition()
            val configLoader = mock<ConfigurationLoader>()
            val taskRunner = mock<TaskRunner>()
            val taskExecutionOrderResolver = mock<TaskExecutionOrderResolver>()
            val errorConsole = mock<Console>()

            val kodein = Kodein {
                bind<ConfigurationLoader>() with instance(configLoader)
                bind<TaskRunner>() with instance(taskRunner)
                bind<TaskExecutionOrderResolver>() with instance(taskExecutionOrderResolver)
                bind<String>(CommonOptions.ConfigurationFileName) with instance("thefile.yml")
                bind<Console>(PrintStreamType.Error) with instance(errorConsole)
            }

            describe("when given one parameter") {
                val result = commandLine.parse(listOf("the-task"), kodein)

                it("indicates that parsing succeeded") {
                    assertThat(result, isA<Succeeded>())
                }

                it("returns a command instance ready for use") {
                    assertThat((result as Succeeded).command, equalTo<Command>(
                        RunTaskCommand("thefile.yml", "the-task", LevelOfParallelismDefaultValueProvider.value, configLoader, taskExecutionOrderResolver, taskRunner, errorConsole)))
                }
            }

            describe("when given one parameter and a level of parallelism") {
                val result = commandLine.parse(listOf("--level-of-parallelism", "123", "the-task"), kodein)

                it("indicates that parsing succeeded") {
                    assertThat(result, isA<Succeeded>())
                }

                it("returns a command instance ready for use with the desired level of parallelism") {
                    assertThat((result as Succeeded).command, equalTo<Command>(
                        RunTaskCommand("thefile.yml", "the-task", 123, configLoader, taskExecutionOrderResolver, taskRunner, errorConsole)))
                }
            }
        }

        describe("when invoked") {
            val configFile = "config.yml"
            val taskName = "the-task"
            val mainTask = Task(taskName, TaskRunConfiguration("the-container"))
            val config = Configuration("the_project", TaskMap(), ContainerMap())
            val expectedTaskExitCode = 123
            val levelOfParallelism = 64

            val configLoader = mock<ConfigurationLoader> {
                on { loadConfig(configFile) } doReturn config
            }

            val redErrorConsole by CreateForEachTest(this) { mock<Console>() }
            val errorConsole by CreateForEachTest(this) {
                mock<Console> {
                    on { withColor(eq(ConsoleColor.Red), any()) } doAnswer {
                        val printStatements = it.getArgument<Console.() -> Unit>(1)
                        printStatements(redErrorConsole)
                    }
                }
            }

            on("when the configuration file can be loaded and the task has no dependencies") {
                val taskExecutionOrderResolver = mock<TaskExecutionOrderResolver> {
                    on { resolveExecutionOrder(config, taskName) } doReturn listOf(mainTask)
                }

                val taskRunner = mock<TaskRunner> {
                    on { run(config, mainTask, levelOfParallelism) } doReturn expectedTaskExitCode
                }

                val command = RunTaskCommand(configFile, taskName, levelOfParallelism, configLoader, taskExecutionOrderResolver, taskRunner, errorConsole)
                val exitCode = command.run()

                it("runs the task") {
                    verify(taskRunner).run(config, mainTask, levelOfParallelism)
                }

                it("returns the exit code of the task") {
                    assertThat(exitCode, equalTo(expectedTaskExitCode))
                }
            }

            describe("when the configuration file can be loaded and the task has a dependency") {
                val otherTask = Task("other-task", TaskRunConfiguration("the-other-container"))

                val taskExecutionOrderResolver = mock<TaskExecutionOrderResolver> {
                    on { resolveExecutionOrder(config, taskName) } doReturn listOf(otherTask, mainTask)
                }

                on("and the dependency finishes with an exit code of 0") {
                    val taskRunner = mock<TaskRunner> {
                        on { run(config, otherTask, levelOfParallelism) } doReturn 0
                        on { run(config, mainTask, levelOfParallelism) } doReturn expectedTaskExitCode
                    }

                    val command = RunTaskCommand(configFile, taskName, levelOfParallelism, configLoader, taskExecutionOrderResolver, taskRunner, errorConsole)
                    val exitCode = command.run()

                    it("runs the dependency task") {
                        verify(taskRunner).run(config, otherTask, levelOfParallelism)
                    }

                    it("runs the main task") {
                        verify(taskRunner).run(config, mainTask, levelOfParallelism)
                    }

                    it("runs the dependency before the main task") {
                        inOrder(taskRunner) {
                            verify(taskRunner).run(config, otherTask, levelOfParallelism)
                            verify(taskRunner).run(config, mainTask, levelOfParallelism)
                        }
                    }

                    it("returns the exit code of the main task") {
                        assertThat(exitCode, equalTo(expectedTaskExitCode))
                    }
                }

                on("and the dependency finishes with a non-zero exit code") {
                    val taskRunner = mock<TaskRunner> {
                        on { run(config, otherTask, levelOfParallelism) } doReturn 1
                    }

                    val command = RunTaskCommand(configFile, taskName, levelOfParallelism, configLoader, taskExecutionOrderResolver, taskRunner, errorConsole)
                    val exitCode = command.run()

                    it("runs the dependency task") {
                        verify(taskRunner).run(config, otherTask, levelOfParallelism)
                    }

                    it("does not run the main task") {
                        verify(taskRunner, never()).run(config, mainTask, levelOfParallelism)
                    }

                    it("returns the exit code of the dependency task") {
                        assertThat(exitCode, equalTo(1))
                    }
                }
            }

            on("when the determining the task execution order fails") {
                val taskExecutionOrderResolver = mock<TaskExecutionOrderResolver> {
                    on { resolveExecutionOrder(config, taskName) } doThrow TaskExecutionOrderResolutionException("Something went wrong.")
                }

                val taskRunner = mock<TaskRunner>()

                val command = RunTaskCommand(configFile, taskName, levelOfParallelism, configLoader, taskExecutionOrderResolver, taskRunner, errorConsole)
                val exitCode = command.run()

                it("prints a message to the output") {
                    verify(redErrorConsole).println("Something went wrong.")
                }

                it("returns a non-zero exit code") {
                    assertThat(exitCode, !equalTo(0))
                }
            }
        }
    }
})
