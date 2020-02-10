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

import batect.config.Configuration
import batect.config.ContainerMap
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.logging.Logger
import batect.logging.Severity
import batect.testutils.InMemoryLogSink
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.hasMessage
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withAdditionalData
import batect.testutils.withLogMessage
import batect.testutils.withMessage
import batect.testutils.withSeverity
import com.natpryce.hamkrest.MatchResult
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
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

        val resolver by createForEachTest { TaskExecutionOrderResolver(suggester, logger) }

        on("resolving the execution order for a task that does not depend on any tasks") {
            val task = Task("some-task", taskRunConfiguration)
            val config = Configuration("some-project", TaskMap(task), ContainerMap())

            val executionOrder by runForEachTest { resolver.resolveExecutionOrder(config, task.name) }

            it("returns just that task") {
                assertThat(executionOrder, equalTo(listOf(task)))
            }

            it("logs the execution order") {
                assertThat(logSink, hasMessage(
                    withSeverity(Severity.Info) and
                        withLogMessage("Resolved task execution order.") and
                        withAdditionalData("executionOrder", listOf(task.name))
                ))
            }
        }

        describe("resolving the execution order for a task that does not exist") {
            val config = Configuration("some-project", TaskMap(), ContainerMap())

            given("there are no suggested task name corrections") {
                beforeEachTest { whenever(suggester.suggestCorrections(config, "some-task")).doReturn(emptyList()) }

                it("throws an appropriate exception without any correction suggestions") {
                    assertThat({ resolver.resolveExecutionOrder(config, "some-task") },
                        throws<TaskExecutionOrderResolutionException>(withMessage("The task 'some-task' does not exist.")))
                }
            }

            given("there is one suggested task name correction") {
                beforeEachTest { whenever(suggester.suggestCorrections(config, "some-task")).doReturn(listOf("some-other-task")) }

                it("throws an appropriate exception without any correction suggestions") {
                    assertThat({ resolver.resolveExecutionOrder(config, "some-task") },
                        throws<TaskExecutionOrderResolutionException>(withMessage("The task 'some-task' does not exist. Did you mean 'some-other-task'?")))
                }
            }

            given("there are two suggested task name corrections") {
                beforeEachTest { whenever(suggester.suggestCorrections(config, "some-task")).doReturn(listOf("some-other-task", "some-other-task-2")) }

                it("throws an appropriate exception without any correction suggestions") {
                    assertThat({ resolver.resolveExecutionOrder(config, "some-task") },
                        throws<TaskExecutionOrderResolutionException>(withMessage("The task 'some-task' does not exist. Did you mean 'some-other-task' or 'some-other-task-2'?")))
                }
            }

            given("there are three suggested task name corrections") {
                beforeEachTest { whenever(suggester.suggestCorrections(config, "some-task")).doReturn(listOf("some-other-task", "some-other-task-2", "some-other-task-3")) }

                it("throws an appropriate exception without any correction suggestions") {
                    assertThat({ resolver.resolveExecutionOrder(config, "some-task") },
                        throws<TaskExecutionOrderResolutionException>(withMessage("The task 'some-task' does not exist. Did you mean 'some-other-task', 'some-other-task-2' or 'some-other-task-3'?")))
                }
            }
        }

        on("resolving the execution order for a task that has a single dependency") {
            val dependencyTask = Task("dependency-task", taskRunConfiguration)
            val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = listOf(dependencyTask.name))
            val config = Configuration("some-project", TaskMap(mainTask, dependencyTask), ContainerMap())

            val executionOrder by runForEachTest { resolver.resolveExecutionOrder(config, mainTask.name) }

            it("schedules the dependency to execute before the main task") {
                assertThat(executionOrder, dependencyTask executesBefore mainTask)
            }
        }

        describe("resolving the execution order for a task that has a dependency that does not exist") {
            val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = listOf("dependency-task"))
            val config = Configuration("some-project", TaskMap(mainTask), ContainerMap())

            given("there are no suggested task name corrections") {
                beforeEachTest { whenever(suggester.suggestCorrections(config, "dependency-task")).doReturn(emptyList()) }

                it("throws an appropriate exception without any correction suggestions") {
                    assertThat({ resolver.resolveExecutionOrder(config, mainTask.name) },
                        throws<TaskExecutionOrderResolutionException>(withMessage("The task 'dependency-task' given as a prerequisite of 'main-task' does not exist.")))
                }
            }

            given("there is one suggested task name correction") {
                beforeEachTest { whenever(suggester.suggestCorrections(config, "dependency-task")).doReturn(listOf("some-other-task")) }

                it("throws an appropriate exception without any correction suggestions") {
                    assertThat({ resolver.resolveExecutionOrder(config, mainTask.name) },
                        throws<TaskExecutionOrderResolutionException>(withMessage("The task 'dependency-task' given as a prerequisite of 'main-task' does not exist. Did you mean 'some-other-task'?")))
                }
            }

            given("there are two suggested task name corrections") {
                beforeEachTest { whenever(suggester.suggestCorrections(config, "dependency-task")).doReturn(listOf("some-other-task", "some-other-task-2")) }

                it("throws an appropriate exception without any correction suggestions") {
                    assertThat({ resolver.resolveExecutionOrder(config, mainTask.name) },
                        throws<TaskExecutionOrderResolutionException>(withMessage("The task 'dependency-task' given as a prerequisite of 'main-task' does not exist. Did you mean 'some-other-task' or 'some-other-task-2'?")))
                }
            }

            given("there are three suggested task name corrections") {
                beforeEachTest { whenever(suggester.suggestCorrections(config, "dependency-task")).doReturn(listOf("some-other-task", "some-other-task-2", "some-other-task-3")) }

                it("throws an appropriate exception without any correction suggestions") {
                    assertThat({ resolver.resolveExecutionOrder(config, mainTask.name) },
                        throws<TaskExecutionOrderResolutionException>(withMessage("The task 'dependency-task' given as a prerequisite of 'main-task' does not exist. Did you mean 'some-other-task', 'some-other-task-2' or 'some-other-task-3'?")))
                }
            }
        }

        on("resolving the execution order for a task that has multiple dependencies") {
            val dependencyTask1 = Task("dependency-task-1", taskRunConfiguration)
            val dependencyTask2 = Task("dependency-task-2", taskRunConfiguration)
            val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = listOf(dependencyTask1.name, dependencyTask2.name))
            val config = Configuration("some-project", TaskMap(mainTask, dependencyTask1, dependencyTask2), ContainerMap())

            val executionOrder by runForEachTest { resolver.resolveExecutionOrder(config, mainTask.name) }

            it("schedules the dependencies to execute before the main task") {
                assertThat(executionOrder, dependencyTask1 executesBefore mainTask)
                assertThat(executionOrder, dependencyTask2 executesBefore mainTask)
            }

            it("schedules the dependencies in the same order they are specified in the configuration file") {
                assertThat(executionOrder, dependencyTask1 executesBefore dependencyTask2)
            }
        }

        on("resolving the execution order for a task that depends on itself") {
            val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = listOf("main-task"))
            val config = Configuration("some-project", TaskMap(mainTask), ContainerMap())

            it("throws an appropriate exception") {
                assertThat({ resolver.resolveExecutionOrder(config, mainTask.name) },
                    throws<TaskExecutionOrderResolutionException>(withMessage("There is a dependency cycle between tasks: task 'main-task' has 'main-task' as a prerequisite.")))
            }
        }

        on("resolving the execution order for a task that depends on a task A, which then depends on a task B") {
            val dependencyTaskB = Task("dependency-task-b", taskRunConfiguration)
            val dependencyTaskA = Task("dependency-task-a", taskRunConfiguration, prerequisiteTasks = listOf(dependencyTaskB.name))
            val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = listOf(dependencyTaskA.name))
            val config = Configuration("some-project", TaskMap(mainTask, dependencyTaskA, dependencyTaskB), ContainerMap())

            val executionOrder by runForEachTest { resolver.resolveExecutionOrder(config, mainTask.name) }

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

            val executionOrder by runForEachTest { resolver.resolveExecutionOrder(config, mainTask.name) }

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

            val executionOrder by runForEachTest { resolver.resolveExecutionOrder(config, mainTask.name) }

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

            val executionOrder by runForEachTest { resolver.resolveExecutionOrder(config, sandwichHasBeenEatenTask.name) }

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
                assertThat({ resolver.resolveExecutionOrder(config, mainTask.name) },
                    throws<TaskExecutionOrderResolutionException>(withMessage("There is a dependency cycle between tasks: task 'main-task' has 'other-task' as a prerequisite, which has 'main-task' as a prerequisite.")))
            }
        }

        on("resolving the execution order for a task that depends on task A, which depends on task B, which depends on the main task") {
            val taskA = Task("task-A", taskRunConfiguration, prerequisiteTasks = listOf("task-B"))
            val taskB = Task("task-B", taskRunConfiguration, prerequisiteTasks = listOf("main-task"))
            val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = listOf("task-A"))
            val config = Configuration("some-project", TaskMap(mainTask, taskA, taskB), ContainerMap())

            it("throws an appropriate exception") {
                assertThat({ resolver.resolveExecutionOrder(config, mainTask.name) },
                    throws<TaskExecutionOrderResolutionException>(withMessage("There is a dependency cycle between tasks: task 'main-task' has 'task-A' as a prerequisite, which has 'task-B' as a prerequisite, which has 'main-task' as a prerequisite.")))
            }
        }

        on("resolving the execution order for a task that depends on task A, which depends on task B, which depends on task A") {
            val taskA = Task("task-A", taskRunConfiguration, prerequisiteTasks = listOf("task-B"))
            val taskB = Task("task-B", taskRunConfiguration, prerequisiteTasks = listOf("task-A"))
            val mainTask = Task("main-task", taskRunConfiguration, prerequisiteTasks = listOf("task-A"))
            val config = Configuration("some-project", TaskMap(mainTask, taskA, taskB), ContainerMap())

            it("throws an appropriate exception") {
                assertThat({ resolver.resolveExecutionOrder(config, mainTask.name) },
                    throws<TaskExecutionOrderResolutionException>(withMessage("There is a dependency cycle between tasks: task 'main-task' has 'task-A' as a prerequisite, which has 'task-B' as a prerequisite, which has 'task-A' as a prerequisite.")))
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
            return actual.map { it.name }.joinToString(", ")
        }
    }
}
