package batect.model

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.mock
import batect.model.steps.BeginTaskStep
import batect.model.steps.TaskStep
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TaskStateMachineProviderSpec : Spek({
    describe("a task state machine provider") {
        on("providing a task state machine") {
            val graph = mock<DependencyGraph>()
            val provider = TaskStateMachineProvider()
            val stateMachine = provider.createStateMachine(graph)
            val firstStep = stateMachine.popNextStep()

            it("queues a 'begin task' step") {
                assertThat(firstStep, equalTo<TaskStep>(BeginTaskStep))
            }
        }
    }
})
