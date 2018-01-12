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
import batect.logging.Logger
import batect.model.BehaviourAfterFailure
import batect.model.DependencyGraph
import batect.model.DependencyGraphProvider
import batect.model.RunOptions
import batect.model.TaskStateMachine
import batect.model.TaskStateMachineProvider
import batect.testutils.InMemoryLogSink
import batect.testutils.imageSourceDoesNotMatter
import batect.ui.EventLogger
import batect.ui.EventLoggerProvider
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.doReturn
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
        val graph = mock<DependencyGraph>()
        val graphProvider = mock<DependencyGraphProvider>()

        val eventLogger = mock<EventLogger>()
        val eventLoggerProvider = mock<EventLoggerProvider> {
            on { getEventLogger(graph) } doReturn eventLogger
        }

        val stateMachine = mock<TaskStateMachine>()
        val executionManager = mock<ParallelExecutionManager>()
        val runOptions = RunOptions("some-task", emptyList(), 64, BehaviourAfterFailure.Cleanup, true)

        val stateMachineProvider = mock<TaskStateMachineProvider> {
            on { createStateMachine(graph, runOptions.behaviourAfterFailure) } doReturn stateMachine
        }

        val executionManagerProvider = mock<ParallelExecutionManagerProvider> {
            on { createParallelExecutionManager(eventLogger, stateMachine, "some-task", runOptions) } doReturn executionManager
        }

        val logger = Logger("some.source", InMemoryLogSink())
        val taskRunner = TaskRunner(eventLoggerProvider, graphProvider, stateMachineProvider, executionManagerProvider, logger)

        beforeEachTest {
            reset(eventLogger)
            reset(stateMachine)
        }

        on("running a task") {
            val container = Container("some-container", imageSourceDoesNotMatter())
            val runConfiguration = TaskRunConfiguration(container.name)
            val task = Task("some-task", runConfiguration)
            val config = Configuration("some-project", TaskMap(task), ContainerMap(container))

            whenever(graphProvider.createGraph(config, task)).thenReturn(graph)
            whenever(executionManager.run()).doReturn(100)

            val exitCode = taskRunner.run(config, task, runOptions)

            it("logs that the task is starting") {
                verify(eventLogger).onTaskStarting("some-task")
            }

            it("runs the task") {
                verify(executionManager).run()
            }

            it("logs that the task is starting before running the task") {
                inOrder(eventLogger, executionManager) {
                    verify(eventLogger).onTaskStarting("some-task")
                    verify(executionManager).run()
                }
            }

            it("returns the exit code from the execution manager") {
                assertThat(exitCode, equalTo(100))
            }
        }
    }
})
