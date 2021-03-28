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

package batect.execution

import batect.cli.CommandLineOptions
import batect.config.Container
import batect.config.Task
import batect.config.TaskRunConfiguration
import batect.telemetry.TelemetrySessionBuilder
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.ui.Console
import batect.ui.OutputStyle
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SessionRunnerSpec : Spek({
    describe("a session runner") {
        val taskName = "the-task"
        val mainTask = Task(taskName, TaskRunConfiguration("the-container"))
        val otherTask = Task("other-task", TaskRunConfiguration("the-other-container"))

        val baseCommandLineOptions = CommandLineOptions(disableCleanupAfterSuccess = true, disableCleanupAfterFailure = true)
        val runOptionsForMainTask = RunOptions(true, baseCommandLineOptions)
        val runOptionsForOtherTask = RunOptions(false, baseCommandLineOptions)

        val taskRunner by createForEachTest { mock<TaskRunner>() }
        val console by createForEachTest { mock<Console>() }
        val imageTaggingValidator by createForEachTest { mock<ImageTaggingValidator>() }
        val telemetrySessionBuilder by createForEachTest { mock<TelemetrySessionBuilder>() }

        given("the task has no prerequisites") {
            val commandLineOptions = baseCommandLineOptions.copy(requestedOutputStyle = OutputStyle.Fancy)
            val taskExecutionOrderResolver = mock<TaskExecutionOrderResolver> {
                on { resolveExecutionOrder(taskName) } doReturn listOf(mainTask)
            }

            given("that task returns a zero exit code") {
                val containers = setOf(
                    Container("the-container", imageSourceDoesNotMatter())
                )

                beforeEachTest {
                    whenever(taskRunner.run(mainTask, runOptionsForMainTask)).thenReturn(TaskRunResult(0, containers))
                }

                val runner by createForEachTest { SessionRunner(taskExecutionOrderResolver, commandLineOptions, taskRunner, console, imageTaggingValidator, telemetrySessionBuilder) }

                given("the task tags all images requested by command line options") {
                    beforeEachTest {
                        whenever(imageTaggingValidator.checkForUntaggedContainers()).doReturn(emptySet())
                    }

                    val exitCode by runForEachTest { runner.runTaskAndPrerequisites(taskName) }

                    it("runs the task") {
                        verify(taskRunner).run(mainTask, runOptionsForMainTask)
                    }

                    it("returns the exit code of the task") {
                        assertThat(exitCode, equalTo(0))
                    }

                    it("does not print any blank lines after the task") {
                        verify(console, never()).println()
                    }

                    it("reports the total number of tasks required to execute the task") {
                        verify(telemetrySessionBuilder).addAttribute("totalTasksToExecute", 1)
                    }

                    it("reports the containers run to the image tagging validator") {
                        verify(imageTaggingValidator).notifyContainersUsed(containers)
                    }
                }

                given("the task does not tag all images requested by command line options") {
                    beforeEachTest {
                        whenever(imageTaggingValidator.checkForUntaggedContainers()).doReturn(setOf("container-1", "container-2", "container-3"))
                    }

                    it("throws an exception") {
                        assertThat({ runner.runTaskAndPrerequisites(taskName) }, throws<UntaggedImagesException>(withContainers("container-1", "container-2", "container-3")))
                    }
                }
            }

            given("that task returns a non-zero exit code") {
                val expectedTaskExitCode = 123

                beforeEachTest {
                    whenever(taskRunner.run(mainTask, runOptionsForMainTask)).thenReturn(TaskRunResult(expectedTaskExitCode, emptySet()))
                }

                val runner by createForEachTest { SessionRunner(taskExecutionOrderResolver, commandLineOptions, taskRunner, console, imageTaggingValidator, telemetrySessionBuilder) }
                val exitCode by runForEachTest { runner.runTaskAndPrerequisites(taskName) }

                it("runs the task") {
                    verify(taskRunner).run(mainTask, runOptionsForMainTask)
                }

                it("returns the exit code of the task") {
                    assertThat(exitCode, equalTo(expectedTaskExitCode))
                }

                it("does not print any blank lines after the task") {
                    verify(console, never()).println()
                }
            }
        }

        given("the task has a prerequisite") {
            val taskExecutionOrderResolver = mock<TaskExecutionOrderResolver> {
                on { resolveExecutionOrder(taskName) } doReturn listOf(otherTask, mainTask)
            }

            given("the dependency finishes with an exit code of 0") {
                val expectedTaskExitCode = 123
                val mainTaskContainers = setOf(
                    Container("the-main-container", imageSourceDoesNotMatter())
                )

                val prerequisiteContainers = setOf(
                    Container("the-prerequisite-container", imageSourceDoesNotMatter())
                )

                beforeEachTest {
                    whenever(taskRunner.run(otherTask, runOptionsForOtherTask)).thenReturn(TaskRunResult(0, prerequisiteContainers))
                    whenever(taskRunner.run(mainTask, runOptionsForMainTask)).thenReturn(TaskRunResult(expectedTaskExitCode, mainTaskContainers))
                }

                given("quiet output mode is not being used") {
                    val commandLineOptions = baseCommandLineOptions.copy(requestedOutputStyle = OutputStyle.Fancy)
                    val runner by createForEachTest { SessionRunner(taskExecutionOrderResolver, commandLineOptions, taskRunner, console, imageTaggingValidator, telemetrySessionBuilder) }

                    val exitCode by runForEachTest { runner.runTaskAndPrerequisites(taskName) }

                    it("runs the dependency task with cleanup on success enabled") {
                        verify(taskRunner).run(otherTask, runOptionsForOtherTask)
                    }

                    it("runs the main task with cleanup on success matching the preference provided by the user") {
                        verify(taskRunner).run(mainTask, runOptionsForMainTask)
                    }

                    it("runs the dependency before the main task, and prints a blank line in between") {
                        inOrder(taskRunner, console) {
                            verify(taskRunner).run(otherTask, runOptionsForOtherTask)
                            verify(console).println()
                            verify(taskRunner).run(mainTask, runOptionsForMainTask)
                        }
                    }

                    it("returns the exit code of the main task") {
                        assertThat(exitCode, equalTo(expectedTaskExitCode))
                    }

                    it("reports the total number of tasks required to execute the task") {
                        verify(telemetrySessionBuilder).addAttribute("totalTasksToExecute", 2)
                    }

                    it("reports the containers run as part of the prerequisite task to the image tagging validator") {
                        verify(imageTaggingValidator).notifyContainersUsed(prerequisiteContainers)
                    }

                    it("reports the containers run as part of the main task to the image tagging validator") {
                        verify(imageTaggingValidator).notifyContainersUsed(mainTaskContainers)
                    }
                }

                given("quiet output mode is being used") {
                    val commandLineOptions = baseCommandLineOptions.copy(requestedOutputStyle = OutputStyle.Quiet)
                    val runner by createForEachTest { SessionRunner(taskExecutionOrderResolver, commandLineOptions, taskRunner, console, imageTaggingValidator, telemetrySessionBuilder) }

                    beforeEachTest { runner.runTaskAndPrerequisites(taskName) }

                    it("runs the dependency before the main task, and does not print a blank line in between") {
                        inOrder(taskRunner, console) {
                            verify(taskRunner).run(otherTask, runOptionsForOtherTask)
                            verify(console, never()).println()
                            verify(taskRunner).run(mainTask, runOptionsForMainTask)
                        }
                    }
                }
            }

            on("and the dependency finishes with a non-zero exit code") {
                beforeEachTest {
                    whenever(taskRunner.run(otherTask, runOptionsForOtherTask)).thenReturn(TaskRunResult(1, emptySet()))
                }

                val commandLineOptions = baseCommandLineOptions.copy(requestedOutputStyle = OutputStyle.Fancy)
                val runner by createForEachTest { SessionRunner(taskExecutionOrderResolver, commandLineOptions, taskRunner, console, imageTaggingValidator, telemetrySessionBuilder) }
                val exitCode by runForEachTest { runner.runTaskAndPrerequisites(taskName) }

                it("runs the dependency task") {
                    verify(taskRunner).run(otherTask, runOptionsForOtherTask)
                }

                it("does not run the main task") {
                    verify(taskRunner, never()).run(mainTask, runOptionsForMainTask)
                }

                it("returns the exit code of the dependency task") {
                    assertThat(exitCode, equalTo(1))
                }
            }
        }
    }
})

private fun withContainers(vararg containers: String): Matcher<UntaggedImagesException> {
    return has(UntaggedImagesException::containerNames, equalTo(containers.toSet()))
}
