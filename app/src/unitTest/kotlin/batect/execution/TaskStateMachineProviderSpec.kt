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

import batect.logging.Logger
import batect.logging.LoggerFactory
import batect.testutils.InMemoryLogSink
import batect.testutils.equalTo
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TaskStateMachineProviderSpec : Spek({
    describe("a task state machine provider") {
        on("providing a task state machine") {
            val graph = mock<ContainerDependencyGraph>()
            val logger = Logger("TaskStateMachine", InMemoryLogSink())

            val loggerFactory = mock<LoggerFactory> {
                on { createLoggerForClass(TaskStateMachine::class) } doReturn logger
            }

            val runOptions = RunOptions("some-task", emptyList(), CleanupOption.Cleanup, CleanupOption.Cleanup, true, emptyMap())
            val provider = TaskStateMachineProvider(mock(), mock(), mock(), loggerFactory)
            val stateMachine = provider.createStateMachine(graph, runOptions)

            it("creates a state machine with the correct logger") {
                assertThat(stateMachine.logger, equalTo(logger))
            }
        }
    }
})
