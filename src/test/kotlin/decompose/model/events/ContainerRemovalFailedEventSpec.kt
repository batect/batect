package decompose.model.events

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import decompose.config.Container
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ContainerRemovalFailedEventSpec : Spek({
    describe("a 'container removal failed' event") {
        val container = Container("some-container", "/some-build-dir")
        val event = ContainerRemovalFailedEvent(container, "Something went wrong")

        on("getting the message to display") {
            it("returns a description of the failure") {
                assert.that(event.messageToDisplay, equalTo("the container 'some-container' couldn't be removed: Something went wrong"))
            }
        }

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assert.that(event.toString(), equalTo("ContainerRemovalFailedEvent(container: 'some-container', message: 'Something went wrong')"))
            }
        }
    }
})
