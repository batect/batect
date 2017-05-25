package decompose

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import decompose.config.Container
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object EventLoggerSpec : Spek({
    describe("an event logger") {
        val output = ByteArrayOutputStream()
        val logger = EventLogger(PrintStream(output))
        val container = Container("the-cool-container", "/build/dir/doesnt/matter")

        beforeEachTest {
            output.reset()
        }

        on("receiving an 'image build started' event") {
            logger.imageBuildStarted(container)

            it("prints a message to the output") {
                assert.that(output.toString(), equalTo("${Emoji.Hammer}  Building 'the-cool-container'...\n"))
            }
        }

        on("receiving a 'command started' event with an explicit command") {
            logger.commandStarted(container, "do-stuff.sh")

            it("prints a message to the output") {
                assert.that(output.toString(), equalTo("${Emoji.Gear}  Running 'do-stuff.sh' in 'the-cool-container'...\n"))
            }
        }

        on("receiving a 'command started' event with no explicit command") {

        }
    }
})
