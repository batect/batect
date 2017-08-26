package decompose.model.events

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import decompose.FinishTaskStep
import decompose.config.Container
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TaskNetworkDeletedEventSpec : Spek({
    describe("a 'task network deleted' event") {
        val event = TaskNetworkDeletedEvent

        describe("being applied") {
            on("when the task is completing normally") {
                val taskContainer = Container("task-container", "/build-dir")
                val context = mock<TaskEventContext> {
                    on { getSinglePastEventOfType<RunningContainerExitedEvent>() } doReturn RunningContainerExitedEvent(taskContainer, 123)
                }

                event.apply(context)

                it("queues a 'finish task' step") {
                    verify(context).queueStep(FinishTaskStep(123))
                }
            }

            on("when the task is aborting") {
                val context = mock<TaskEventContext> {
                    on { isAborting } doReturn true
                }

                event.apply(context)

                it("does not queue any further work") {
                    verify(context, never()).queueStep(any())
                }
            }
        }

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assert.that(event.toString(), equalTo("TaskNetworkDeletedEvent"))
            }
        }
    }
})
