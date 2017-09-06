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

package batect

import batect.config.Configuration
import batect.config.Container
import batect.config.ContainerMap
import batect.config.Task
import batect.config.TaskMap
import batect.config.TaskRunConfiguration
import batect.model.DependencyGraph
import batect.model.DependencyGraphProvider
import batect.model.TaskStateMachine
import batect.model.TaskStateMachineProvider
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TaskRunnerSpec : Spek({
    describe("a task runner") {
        val eventLogger = mock<EventLogger>()
        val graph = mock<DependencyGraph>()
        val graphProvider = mock<DependencyGraphProvider>()
        val stateMachine = mock<TaskStateMachine>()
        val executionManager = mock<ParallelExecutionManager>()

        val stateMachineProvider = mock<TaskStateMachineProvider> {
            on { createStateMachine(graph) } doReturn stateMachine
        }

        val executionManagerProvider = mock<ParallelExecutionManagerProvider> {
            on { createParallelExecutionManager(eq(stateMachine), eq("some-task")) } doReturn executionManager
        }

        val taskRunner = TaskRunner(eventLogger, graphProvider, stateMachineProvider, executionManagerProvider)

        beforeEachTest {
            reset(eventLogger)
            reset(stateMachine)
        }

        on("attempting to run a task that does not exist") {
            val config = Configuration("some-project", TaskMap(), ContainerMap())
            val exitCode = taskRunner.run(config, "some-task")

            it("logs that the task does not exist") {
                verify(eventLogger).logTaskDoesNotExist("some-task")
            }

            it("returns a non-zero exit code") {
                assertThat(exitCode, !equalTo(0))
            }
        }

        on("running a task that exists") {
            val container = Container("some-container", "/some-build-dir")
            val runConfiguration = TaskRunConfiguration(container.name)
            val task = Task("some-task", runConfiguration)
            val config = Configuration("some-project", TaskMap(task), ContainerMap(container))

            whenever(graphProvider.createGraph(config, task)).thenReturn(graph)
            whenever(executionManager.run()).doReturn(100)

            val exitCode = taskRunner.run(config, "some-task")

            it("resets the event logger") {
                verify(eventLogger).reset()
            }

            it("returns the exit code from the execution manager") {
                assertThat(exitCode, equalTo(100))
            }

            it("resets the event logger before running the task") {
                inOrder(eventLogger, executionManager) {
                    verify(eventLogger).reset()
                    verify(executionManager).run()
                }
            }
        }
    }
})
