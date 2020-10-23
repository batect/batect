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

package batect.execution

import batect.cli.CommandLineOptions
import batect.config.Configuration
import batect.config.ContainerMap
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.logging.Logger
import batect.logging.Severity
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.logging.InMemoryLogSink
import batect.testutils.logging.hasMessage
import batect.testutils.logging.withAdditionalData
import batect.testutils.logging.withLogMessage
import batect.testutils.logging.withSeverity
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasSize
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TaskExecutionOrderResolverSpec : Spek({
    describe("a task execution order resolver") {
        val suggester by createForEachTest { mock<TaskSuggester>() }
        val logSink by createForEachTest { InMemoryLogSink() }
        val logger by createForEachTest { Logger("some.source", logSink) }
        val taskRunConfiguration = TaskRunConfiguration("some-container")

        given("skipping prerequisites is not enabled") {
            val commandLineOptions by createForEachTest { CommandLineOptions(skipPrerequisites = false) }

            fun resolveExecutionOrder(config: Configuration, taskName: String): List<Task> {
                val resolver = TaskExecutionOrderResolver(config, commandLineOptions, suggester, logger)
                return resolver.resolveExecutionOrder(taskName)
            }

            on("resolving the execution order for a task that does not depend on any tasks") {
                val task = Task("some-task", taskRunConfiguration)
                val config = Configuration("some-project", TaskMap(task), ContainerMap())

                val executionOrder by runForEachTest { resolveExecutionOrder(config, task.name) }

                it("returns just that task") {
                    assertThat(executionOrder, equalTo(listOf(task)))
                }

                it("logs the execution order") {
                    assertThat(
                        logSink,
                        hasMessage(
                            withSeverity(Severity.Info) and
                                withLogMessage("Resolved task execution order.") and
                                withAdditionalData("executionOrder", listOf(task.name)) and
                                withAdditionalData("skipPrerequisites", false)
                        )
                    )
                }
            }

            describe("resolving the execution order for a task that does not exist") {
                val config = Configuration("some-project", TaskMap(), ContainerMap())

                given("there are no suggested task name corrections") {
                    beforeEachTest { whenever(suggester.suggestCorrections(config, "some-task")).doReturn(emptyList()) }

                    it("throws an appropriate exception without any correction suggestions") {
                        assertThat(
                            { resolveExecutionOrder(config, "some-task") },
                            throws<TaskDoesNotExistException>(withMessage("The task 'some-task' does not exist. (Run './batect --list-tasks' for a list of all tasks in this project, or './batect --help' for help.)"))
                        )
                    }
                }

                given("there is one suggested task name correction") {
                    beforeEachTest { whenever(suggester.suggestCorrections(config, "some-task")).doReturn(listOf("some-other-task")) }

                    it("throws an appropriate exception without any correction suggestions") {
                        assertThat(
                            { resolveExecutionOrder(config, "some-task") },
                            throws<TaskDoesNotExistException>(withMessage("The task 'some-task' does not exist. Did you mean 'some-other-task'? (Run './batect --list-tasks' for a list of all tasks in this project, or './batect --help' for help.)"))
                        )
                    }
                }

                given("there are two suggested task name corrections") {
                    beforeEachTest { whenever(suggester.suggestCorrections(config, "some-task")).doReturn(listOf("some-other-task", "some-other-task-2")) }

                    it("throws an appropriate exception without any correction suggestions") {
                        assertThat(
                            { resolveExecutionOrder(config, "some-task") },
                            throws<TaskDoesNotExistException>(withMessage("The task 'some-task' does not exist. Did you mean 'some-other-task' or 'some-other-task-2'? (Run './batect --list-tasks' for a list of all tasks in this project, or './batect --help' for help.)"))
                        )
                    }
                }

                given("there are three suggested task name corrections") {
                    beforeEachTest { whenever(suggester.suggestCorrections(config, "some-task")).doReturn(listOf("some-other-task", "some-other-task-2", "some-other-task-3")) }

                    it("throws an appropriate exception without any correction suggestions") {
                        assertThat(
                            { resolveExecutionOrder(config, "some-task") },
                            throws<TaskDoesNotExistException>(withMessage("The task 'some-task' does not exist. Did you mean 'some-other-task', 'some-other-task-2' or 'some-other-task-3'? (Run './batect --list-tasks' for a list of all tasks in this project, or './batect --help' for help.)"))
                        )
                    }
                }
            }

            describe("resolving the execution order for a task that has a single dependency") {
                mapOf(
                    "the dependent task's name is given explicitly" to "dependency-task",
                    "the dependent task's name is given with a wildcard that matches part of the end of the dependent task's name" to "dependency-*",
                    "the dependent task's name is given with a wildcard that matches part of the start of the dependent task's name" to "*cy-task",
                    "the dependent task's name is given with a wildcard that matches part of the middle of the dependent task's name" to "dependen*-task",
                    "the dependent task's name is given with a wildcard that matches a single character at the start of the dependent task's name" to "*ependency-task",
                    "the dependent task's name is given with a wildcard that matches a single character at the end of the dependent task's name" to "dependency-tas*",
                    "the dependent task's name is given with a wildcard that matches a single character in the middle of the dependent task's name" to "dependenc*-task",
                    "the dependent task's name is given with a wildcard that matches no characters of the dependent task's name" to "dependency-task*",
                ).forEach { description, prerequisiteSpec ->
                    given(description) {
                        val dependencyTask = Task("dependency-task", taskRunConfiguration)
                        val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = listOf(prerequisiteSpec))
                        val config = Configuration("some-project", TaskMap(mainTask, dependencyTask), ContainerMap())

                        val executionOrder by runForEachTest { resolveExecutionOrder(config, mainTask.name) }

                        it("schedules the dependency to execute before the main task") {
                            assertThat(executionOrder, dependencyTask executesBefore mainTask)
                        }

                        it("logs the execution order") {
                            assertThat(
                                logSink,
                                hasMessage(
                                    withSeverity(Severity.Info) and
                                        withLogMessage("Resolved task execution order.") and
                                        withAdditionalData("executionOrder", listOf(dependencyTask.name, mainTask.name)) and
                                        withAdditionalData("skipPrerequisites", false)
                                )
                            )
                        }
                    }
                }
            }

            describe("resolving the execution order for a task that has a wildcard dependency that does not match any tasks") {
                mapOf(
                    "the pattern has a different case to a defined task" to "Dependency-*",
                    "the pattern does not match the full name of a defined task from the start" to "pendency-*",
                    "the pattern does not match the full name of a defined task to the end" to "*-ta"
                ).forEach { description, prerequisiteSpec ->
                    given(description) {
                        val dependencyTask = Task("dependency-task", taskRunConfiguration)
                        val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = listOf(prerequisiteSpec))
                        val config = Configuration("some-project", TaskMap(mainTask, dependencyTask), ContainerMap())

                        val executionOrder by runForEachTest { resolveExecutionOrder(config, mainTask.name) }

                        it("returns only the main task") {
                            assertThat(executionOrder, equalTo(listOf(mainTask)))
                        }

                        it("logs the execution order") {
                            assertThat(
                                logSink,
                                hasMessage(
                                    withSeverity(Severity.Info) and
                                        withLogMessage("Resolved task execution order.") and
                                        withAdditionalData("executionOrder", listOf(mainTask.name)) and
                                        withAdditionalData("skipPrerequisites", false)
                                )
                            )
                        }
                    }
                }
            }

            describe("resolving the execution order for a task that has a dependency that does not exist") {
                val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = listOf("dependency-task"))
                val config = Configuration("some-project", TaskMap(mainTask), ContainerMap())

                given("there are no suggested task name corrections") {
                    beforeEachTest { whenever(suggester.suggestCorrections(config, "dependency-task")).doReturn(emptyList()) }

                    it("throws an appropriate exception without any correction suggestions") {
                        assertThat(
                            { resolveExecutionOrder(config, mainTask.name) },
                            throws<PrerequisiteTaskDoesNotExistException>(withMessage("The task 'dependency-task' given as a prerequisite of 'main-task' does not exist."))
                        )
                    }
                }

                given("there is one suggested task name correction") {
                    beforeEachTest { whenever(suggester.suggestCorrections(config, "dependency-task")).doReturn(listOf("some-other-task")) }

                    it("throws an appropriate exception without any correction suggestions") {
                        assertThat(
                            { resolveExecutionOrder(config, mainTask.name) },
                            throws<PrerequisiteTaskDoesNotExistException>(withMessage("The task 'dependency-task' given as a prerequisite of 'main-task' does not exist. Did you mean 'some-other-task'?"))
                        )
                    }
                }

                given("there are two suggested task name corrections") {
                    beforeEachTest { whenever(suggester.suggestCorrections(config, "dependency-task")).doReturn(listOf("some-other-task", "some-other-task-2")) }

                    it("throws an appropriate exception without any correction suggestions") {
                        assertThat(
                            { resolveExecutionOrder(config, mainTask.name) },
                            throws<PrerequisiteTaskDoesNotExistException>(withMessage("The task 'dependency-task' given as a prerequisite of 'main-task' does not exist. Did you mean 'some-other-task' or 'some-other-task-2'?"))
                        )
                    }
                }

                given("there are three suggested task name corrections") {
                    beforeEachTest { whenever(suggester.suggestCorrections(config, "dependency-task")).doReturn(listOf("some-other-task", "some-other-task-2", "some-other-task-3")) }

                    it("throws an appropriate exception without any correction suggestions") {
                        assertThat(
                            { resolveExecutionOrder(config, mainTask.name) },
                            throws<PrerequisiteTaskDoesNotExistException>(withMessage("The task 'dependency-task' given as a prerequisite of 'main-task' does not exist. Did you mean 'some-other-task', 'some-other-task-2' or 'some-other-task-3'?"))
                        )
                    }
                }
            }

            on("resolving the execution order for a task that has multiple dependencies") {
                mapOf(
                    "the tasks are given explicitly" to listOf("dependency-task-1", "dependency-task-2"),
                    "the tasks are given with a wildcard" to listOf("dependency-task-*"),
                    "the tasks are given with a wildcard and both are also specified before the entry with a wildcard" to listOf("dependency-task-1", "dependency-task-2", "dependency-task-*"),
                    "the tasks are given with a wildcard and one is also specified before the entry with a wildcard" to listOf("dependency-task-1", "dependency-task-*"),
                    "the tasks are given with a wildcard and one is also specified after the entry with a wildcard" to listOf("dependency-task-*", "dependency-task-2"),
                ).forEach { description, prerequisites ->
                    given(description) {
                        val dependencyTask1 = Task("dependency-task-1", taskRunConfiguration)
                        val dependencyTask2 = Task("dependency-task-2", taskRunConfiguration)
                        val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = prerequisites)
                        val config = Configuration("some-project", TaskMap(mainTask, dependencyTask1, dependencyTask2), ContainerMap())

                        val executionOrder by runForEachTest { resolveExecutionOrder(config, mainTask.name) }

                        it("schedules the dependencies to execute before the main task") {
                            assertThat(executionOrder, dependencyTask1 executesBefore mainTask)
                            assertThat(executionOrder, dependencyTask2 executesBefore mainTask)
                        }

                        it("schedules the dependencies in the same order they are specified in the configuration file, or sorted alphabetically if a wildcard is used") {
                            assertThat(executionOrder, dependencyTask1 executesBefore dependencyTask2)
                        }

                        it("does not return any task more than once") {
                            assertThat(executionOrder.toSet(), hasSize(equalTo(3)))
                        }
                    }
                }

                given("the tasks are given with a wildcard and the tasks are not defined in alphabetical order") {
                    val dependencyTask1 = Task("dependency-task-1", taskRunConfiguration)
                    val dependencyTask2 = Task("dependency-task-2", taskRunConfiguration)
                    val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = listOf("dependency-task-*"))
                    val config = Configuration("some-project", TaskMap(mainTask, dependencyTask2, dependencyTask1), ContainerMap())

                    val executionOrder by runForEachTest { resolveExecutionOrder(config, mainTask.name) }

                    it("schedules the dependencies to execute before the main task") {
                        assertThat(executionOrder, dependencyTask1 executesBefore mainTask)
                        assertThat(executionOrder, dependencyTask2 executesBefore mainTask)
                    }

                    it("schedules the dependencies in alphabetical order") {
                        assertThat(executionOrder, dependencyTask1 executesBefore dependencyTask2)
                    }
                }

                given("the tasks are given with a wildcard and an explicit task that would otherwise sort in a different order alphabetically") {
                    val dependencyTask1 = Task("dependency-task-1", taskRunConfiguration)
                    val dependencyTask2 = Task("dependency-task-2", taskRunConfiguration)
                    val dependencyTask3 = Task("dependency-task-3", taskRunConfiguration)
                    val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = listOf("dependency-task-2", "dependency-task-*"))
                    val config = Configuration("some-project", TaskMap(mainTask, dependencyTask2, dependencyTask1, dependencyTask3), ContainerMap())

                    val executionOrder by runForEachTest { resolveExecutionOrder(config, mainTask.name) }

                    it("schedules the dependencies to execute before the main task") {
                        assertThat(executionOrder, dependencyTask1 executesBefore mainTask)
                        assertThat(executionOrder, dependencyTask2 executesBefore mainTask)
                        assertThat(executionOrder, dependencyTask3 executesBefore mainTask)
                    }

                    it("respects any dependencies given explicitly before the wildcard pattern") {
                        assertThat(executionOrder, dependencyTask2 executesBefore dependencyTask1)
                    }

                    it("schedules the remaining dependencies in alphabetical order") {
                        assertThat(executionOrder, dependencyTask1 executesBefore dependencyTask3)
                    }

                    it("does not return any task more than once") {
                        assertThat(executionOrder.toSet(), hasSize(equalTo(4)))
                    }
                }
            }

            on("resolving the execution order for a task that depends on itself") {
                val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = listOf("main-task"))
                val config = Configuration("some-project", TaskMap(mainTask), ContainerMap())

                it("throws an appropriate exception") {
                    assertThat(
                        { resolveExecutionOrder(config, mainTask.name) },
                        throws<TaskDependencyCycleException>(withMessage("There is a dependency cycle between tasks: task 'main-task' has 'main-task' as a prerequisite."))
                    )
                }
            }

            on("resolving the execution order for a task that depends on a task A, which then depends on a task B") {
                val dependencyTaskB = Task("dependency-task-b", taskRunConfiguration)
                val dependencyTaskA = Task("dependency-task-a", taskRunConfiguration, prerequisiteTasks = listOf(dependencyTaskB.name))
                val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = listOf(dependencyTaskA.name))
                val config = Configuration("some-project", TaskMap(mainTask, dependencyTaskA, dependencyTaskB), ContainerMap())

                val executionOrder by runForEachTest { resolveExecutionOrder(config, mainTask.name) }

                it("schedules task A to execute before the main task") {
                    assertThat(executionOrder, dependencyTaskA executesBefore mainTask)
                }

                it("schedules task B to execute before task A") {
                    assertThat(executionOrder, dependencyTaskB executesBefore dependencyTaskA)
                }
            }

            on("resolving the execution order for a task that depends on tasks A and B, where B also depends on A") {
                val dependencyTaskA = Task("dependency-task-a", taskRunConfiguration)
                val dependencyTaskB = Task("dependency-task-b", taskRunConfiguration, prerequisiteTasks = listOf(dependencyTaskA.name))
                val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = listOf(dependencyTaskA.name, dependencyTaskB.name))
                val config = Configuration("some-project", TaskMap(mainTask, dependencyTaskA, dependencyTaskB), ContainerMap())

                val executionOrder by runForEachTest { resolveExecutionOrder(config, mainTask.name) }

                it("schedules task B to execute before the main task") {
                    assertThat(executionOrder, dependencyTaskB executesBefore mainTask)
                }

                it("schedules task A to execute before task B") {
                    assertThat(executionOrder, dependencyTaskA executesBefore dependencyTaskB)
                }
            }

            on("resolving the execution order for a task that depends on tasks A and B, where B also depends on A and B is listed first in the main task's list of prerequisites") {
                val dependencyTaskA = Task("dependency-task-a", taskRunConfiguration)
                val dependencyTaskB = Task("dependency-task-b", taskRunConfiguration, prerequisiteTasks = listOf(dependencyTaskA.name))
                val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = listOf(dependencyTaskB.name, dependencyTaskA.name))
                val config = Configuration("some-project", TaskMap(mainTask, dependencyTaskA, dependencyTaskB), ContainerMap())

                val executionOrder by runForEachTest { resolveExecutionOrder(config, mainTask.name) }

                it("schedules task B to execute before the main task") {
                    assertThat(executionOrder, dependencyTaskB executesBefore mainTask)
                }

                it("schedules task A to execute before task B") {
                    assertThat(executionOrder, dependencyTaskA executesBefore dependencyTaskB)
                }
            }

            on("resolving the execution order for a complex set of tasks from issue 53 (https://github.com/batect/batect/issues/53)") {
                val getSandwichContentsTask = Task("getSandwichContents", taskRunConfiguration)
                val putContentsInBreadTask = Task("putContentsInBread", taskRunConfiguration, prerequisiteTasks = listOf("getSandwichContents"))
                val prepareTheTableTask = Task("prepareTheTable", taskRunConfiguration)
                val makeTheSandwichTask = Task("makeTheSandwich", taskRunConfiguration, prerequisiteTasks = listOf("putContentsInBread", "prepareTheTable"))
                val takeABiteOfTheSandwichTask = Task("takeABiteOfTheSandwich", taskRunConfiguration, prerequisiteTasks = listOf("prepareTheTable"))
                val eatTheSandwichTask = Task("eatTheSandwich", taskRunConfiguration, prerequisiteTasks = listOf("takeABiteOfTheSandwich"))
                val sandwichHasBeenEatenTask = Task("sandwichHasBeenEaten", taskRunConfiguration, prerequisiteTasks = listOf("makeTheSandwich", "eatTheSandwich"))
                val config = Configuration("some-project", TaskMap(getSandwichContentsTask, putContentsInBreadTask, prepareTheTableTask, makeTheSandwichTask, takeABiteOfTheSandwichTask, eatTheSandwichTask, sandwichHasBeenEatenTask), ContainerMap())

                val executionOrder by runForEachTest { resolveExecutionOrder(config, sandwichHasBeenEatenTask.name) }

                it("schedules the tasks to run in an order that respects the relative ordering of each prerequisite list") {
                    assertThat(executionOrder, getSandwichContentsTask executesBefore putContentsInBreadTask)
                    assertThat(executionOrder, putContentsInBreadTask executesBefore prepareTheTableTask)
                    assertThat(executionOrder, prepareTheTableTask executesBefore makeTheSandwichTask)
                    assertThat(executionOrder, makeTheSandwichTask executesBefore takeABiteOfTheSandwichTask)
                    assertThat(executionOrder, takeABiteOfTheSandwichTask executesBefore eatTheSandwichTask)
                    assertThat(executionOrder, eatTheSandwichTask executesBefore sandwichHasBeenEatenTask)
                }
            }

            on("resolving the execution order for a task that depends on a second task that depends on the first task") {
                val otherTask = Task("other-task", taskRunConfiguration, prerequisiteTasks = listOf("main-task"))
                val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = listOf("other-task"))
                val config = Configuration("some-project", TaskMap(mainTask, otherTask), ContainerMap())

                it("throws an appropriate exception") {
                    assertThat(
                        { resolveExecutionOrder(config, mainTask.name) },
                        throws<TaskDependencyCycleException>(withMessage("There is a dependency cycle between tasks: task 'main-task' has 'other-task' as a prerequisite, which has 'main-task' as a prerequisite."))
                    )
                }
            }

            on("resolving the execution order for a task that depends on task A, which depends on task B, which depends on the main task") {
                val taskA = Task("task-A", taskRunConfiguration, prerequisiteTasks = listOf("task-B"))
                val taskB = Task("task-B", taskRunConfiguration, prerequisiteTasks = listOf("main-task"))
                val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = listOf("task-A"))
                val config = Configuration("some-project", TaskMap(mainTask, taskA, taskB), ContainerMap())

                it("throws an appropriate exception") {
                    assertThat(
                        { resolveExecutionOrder(config, mainTask.name) },
                        throws<TaskDependencyCycleException>(withMessage("There is a dependency cycle between tasks: task 'main-task' has 'task-A' as a prerequisite, which has 'task-B' as a prerequisite, which has 'main-task' as a prerequisite."))
                    )
                }
            }

            on("resolving the execution order for a task that depends on task A, which depends on task B, which depends on task A") {
                val taskA = Task("task-A", taskRunConfiguration, prerequisiteTasks = listOf("task-B"))
                val taskB = Task("task-B", taskRunConfiguration, prerequisiteTasks = listOf("task-A"))
                val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = listOf("task-A"))
                val config = Configuration("some-project", TaskMap(mainTask, taskA, taskB), ContainerMap())

                it("throws an appropriate exception") {
                    assertThat(
                        { resolveExecutionOrder(config, mainTask.name) },
                        throws<TaskDependencyCycleException>(withMessage("There is a dependency cycle between tasks: task 'main-task' has 'task-A' as a prerequisite, which has 'task-B' as a prerequisite, which has 'task-A' as a prerequisite."))
                    )
                }
            }
        }

        given("skipping prerequisites is enabled") {
            val commandLineOptions by createForEachTest { CommandLineOptions(skipPrerequisites = true) }

            fun resolveExecutionOrder(config: Configuration, taskName: String): List<Task> {
                val resolver = TaskExecutionOrderResolver(config, commandLineOptions, suggester, logger)
                return resolver.resolveExecutionOrder(taskName)
            }

            on("resolving the execution order for a task that does not depend on any tasks") {
                val task = Task("some-task", taskRunConfiguration)
                val config = Configuration("some-project", TaskMap(task), ContainerMap())

                val executionOrder by runForEachTest { resolveExecutionOrder(config, task.name) }

                it("returns just that task") {
                    assertThat(executionOrder, equalTo(listOf(task)))
                }

                it("logs the execution order") {
                    assertThat(
                        logSink,
                        hasMessage(
                            withSeverity(Severity.Info) and
                                withLogMessage("Resolved task execution order.") and
                                withAdditionalData("executionOrder", listOf(task.name)) and
                                withAdditionalData("skipPrerequisites", true)
                        )
                    )
                }
            }

            on("resolving the execution order for a task that has multiple dependencies") {
                val dependencyTask1 = Task("dependency-task-1", taskRunConfiguration)
                val dependencyTask2 = Task("dependency-task-2", taskRunConfiguration)
                val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = listOf(dependencyTask1.name, dependencyTask2.name))
                val config = Configuration("some-project", TaskMap(mainTask, dependencyTask1, dependencyTask2), ContainerMap())

                val executionOrder by runForEachTest { resolveExecutionOrder(config, mainTask.name) }

                it("returns just the main task") {
                    assertThat(executionOrder, equalTo(listOf(mainTask)))
                }

                it("logs the execution order") {
                    assertThat(
                        logSink,
                        hasMessage(
                            withSeverity(Severity.Info) and
                                withLogMessage("Resolved task execution order.") and
                                withAdditionalData("executionOrder", listOf(mainTask.name)) and
                                withAdditionalData("skipPrerequisites", true)
                        )
                    )
                }
            }
        }
    }
})

infix fun Task.executesBefore(secondTask: Task): Matcher.Primitive<List<Task>> {
    val firstTask = this

    return object : Matcher.Primitive<List<Task>>() {
        override val description: String
            get() = "has ${firstTask.name} executing before ${secondTask.name}"

        override val negatedDescription: String
            get() = "does not have ${firstTask.name} executing before ${secondTask.name}"

        override fun invoke(actual: List<Task>): MatchResult {
            val indexOfFirst = actual.indexOf(firstTask)
            val indexOfSecond = actual.indexOf(secondTask)

            if (indexOfFirst == -1) {
                return MatchResult.Mismatch("${firstTask.name} was not in the list of tasks to execute (${formattedListOfTasks(actual)})")
            }

            if (indexOfSecond == -1) {
                return MatchResult.Mismatch("${secondTask.name} was not in the list of tasks to execute (${formattedListOfTasks(actual)})")
            }

            if (indexOfFirst > indexOfSecond) {
                return MatchResult.Mismatch("${secondTask.name} was scheduled to execute before ${firstTask.name} in list of tasks to execute (${formattedListOfTasks(actual)})")
            }

            return MatchResult.Match
        }

        private fun formattedListOfTasks(actual: List<Task>): String {
            return actual.joinToString(", ") { it.name }
        }
    }
}
