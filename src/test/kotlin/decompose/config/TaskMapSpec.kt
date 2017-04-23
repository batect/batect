package decompose.config

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasSize
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.throws
import decompose.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TaskMapSpec : Spek({
    describe("a set of tasks") {
        describe("creating a set of tasks") {
            on("creating an empty set") {
                val set = TaskMap()

                it("has no entries") {
                    assert.that(set.entries, isEmpty)
                }

                it("has no keys") {
                    assert.that(set.keys, isEmpty)
                }

                it("has no values") {
                    assert.that(set.values, isEmpty)
                }

                it("has a size of 0") {
                    assert.that(set.size, equalTo(0))
                }

                it("reports that it is empty") {
                    assert.that(set.isEmpty(), equalTo(true))
                }
            }

            on("creating a set with a single task") {
                val task = Task("the_task", TaskRunConfiguration("container", null))
                val set = TaskMap(task)

                it("has one entry") {
                    val entries = set.entries
                    assert.that(entries, hasSize(equalTo(1)))
                    assert.that(entries.map { it.key }.toSet(), equalTo(setOf(task.name)))
                    assert.that(entries.map { it.value }.toSet(), equalTo(setOf(task)))
                }

                it("has one key") {
                    assert.that(set.keys, equalTo(setOf(task.name)))
                }

                it("has one value") {
                    assert.that(set.values.toList(), equalTo(listOf(task)))
                }

                it("has a size of 1") {
                    assert.that(set.size, equalTo(1))
                }

                it("reports that it is not empty") {
                    assert.that(set.isEmpty(), equalTo(false))
                }

                it("reports that it contains the task's name") {
                    assert.that(set.containsKey(task.name), equalTo(true))
                }

                it("reports that it contains the task") {
                    assert.that(set.containsValue(task), equalTo(true))
                }

                it("returns the task when accessing it by name") {
                    assert.that(set.get(task.name), equalTo(task))
                }
            }

            on("creating a set with two tasks with different names") {
                val task1 = Task("task-1", TaskRunConfiguration("container-1", null))
                val task2 = Task("task-2", TaskRunConfiguration("container-2", null))
                val set = TaskMap(task1, task2)

                it("has two entries") {
                    val entries = set.entries
                    assert.that(entries, hasSize(equalTo(2)))
                    assert.that(entries.map { it.key }.toSet(), equalTo(setOf(task1.name, task2.name)))
                    assert.that(entries.map { it.value }.toSet(), equalTo(setOf(task1, task2)))
                }

                it("has two keys") {
                    assert.that(set.keys, equalTo(setOf(task1.name, task2.name)))
                }

                it("has two values") {
                    assert.that(set.values.toList(), equalTo(listOf(task1, task2)))
                }

                it("has a size of 2") {
                    assert.that(set.size, equalTo(2))
                }

                it("reports that it is not empty") {
                    assert.that(set.isEmpty(), equalTo(false))
                }

                it("reports that it contains the tasks' names") {
                    assert.that(set.containsKey(task1.name), equalTo(true))
                    assert.that(set.containsKey(task2.name), equalTo(true))
                }

                it("reports that it contains the tasks") {
                    assert.that(set.containsValue(task1), equalTo(true))
                    assert.that(set.containsValue(task2), equalTo(true))
                }

                it("returns the tasks when accessing them by name") {
                    assert.that(set.get(task1.name), equalTo(task1))
                    assert.that(set.get(task2.name), equalTo(task2))
                }
            }

            on("creating a set with two tasks with the same name") {
                it("fails with an appropriate error message") {
                    val taskName = "the_task"
                    val task1 = Task(taskName, TaskRunConfiguration("container-1", null))
                    val task2 = Task(taskName, TaskRunConfiguration("container-2", null))

                    assert.that({ TaskMap(task1, task2) }, throws(withMessage("Cannot create a TaskSet where a task name is used more than once. Duplicated task names: $taskName")))
                }
            }

            on("creating a set with four tasks with each name duplicated") {
                it("fails with an appropriate error message") {
                    val taskName1 = "task1"
                    val taskName2 = "task2"
                    val task1 = Task(taskName1, TaskRunConfiguration("container-1", null))
                    val task2 = Task(taskName1, TaskRunConfiguration("container-2", null))
                    val task3 = Task(taskName2, TaskRunConfiguration("container-3", null))
                    val task4 = Task(taskName2, TaskRunConfiguration("container-4", null))

                    assert.that({ TaskMap(task1, task2, task3, task4) }, throws(withMessage("Cannot create a TaskSet where a task name is used more than once. Duplicated task names: $taskName1, $taskName2")))
                }
            }
        }
    }
})
