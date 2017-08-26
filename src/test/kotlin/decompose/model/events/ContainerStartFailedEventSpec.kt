package decompose.model.events

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import decompose.config.Container
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ContainerStartFailedEventSpec : Spek({
    describe("a 'container creation failed' event") {
        val container = Container("container-1", "/build-dir")
        val event = ContainerStartFailedEvent(container, "Something went wrong")

        on("getting the message to display to the user") {
            it("returns an appropriate message") {
                assertThat(event.messageToDisplay, equalTo("Could not start container 'container-1': Something went wrong"))
            }
        }

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assertThat(event.toString(), equalTo("ContainerStartFailedEvent(container: 'container-1', message: 'Something went wrong')"))
            }
        }
    }
})
