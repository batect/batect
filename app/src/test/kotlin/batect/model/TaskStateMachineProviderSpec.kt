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

package batect.model

import batect.logging.Logger
import batect.logging.LoggerFactory
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.mock
import batect.model.steps.BeginTaskStep
import batect.model.steps.TaskStep
import batect.testutils.InMemoryLogSink
import com.nhaarman.mockito_kotlin.doReturn
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TaskStateMachineProviderSpec : Spek({
    describe("a task state machine provider") {
        on("providing a task state machine") {
            val graph = mock<DependencyGraph>()
            val logger = Logger("TaskStateMachine", InMemoryLogSink())

            val loggerFactory = mock<LoggerFactory> {
                on { createLoggerForClass(TaskStateMachine::class) } doReturn logger
            }

            val behaviourAfterFailure = BehaviourAfterFailure.Cleanup
            val provider = TaskStateMachineProvider(loggerFactory)
            val stateMachine = provider.createStateMachine(graph, behaviourAfterFailure)
            val firstStep = stateMachine.popNextStep()

            it("creates a state machine with the correct logger") {
                assertThat(stateMachine.logger, equalTo(logger))
            }

            it("creates a state machine with the provided behaviour after failure") {
                assertThat(stateMachine.behaviourAfterFailure, equalTo(behaviourAfterFailure))
            }

            it("queues a 'begin task' step") {
                assertThat(firstStep, equalTo<TaskStep>(BeginTaskStep))
            }
        }
    }
})
