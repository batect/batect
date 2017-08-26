package decompose.model.events

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TaskNetworkCreationFailedEventSpec : Spek({
    describe("a 'task network creation failed' event") {
        val event = TaskNetworkCreationFailedEvent("Something went wrong")

        on("getting the message to display to the user") {
            it("returns an appropriate message") {
                assertThat(event.messageToDisplay, equalTo("Could not create network for task: Something went wrong"))
            }
        }

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assertThat(event.toString(), equalTo("TaskNetworkCreationFailedEvent(message: 'Something went wrong')"))
            }
        }
    }
})
