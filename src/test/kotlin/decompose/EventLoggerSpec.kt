package decompose

import com.nhaarman.mockito_kotlin.*
import decompose.config.Container
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object EventLoggerSpec : Spek({
    describe("an event logger") {
        val whiteConsole = mock<Console>()
        val console = mock<Console> {
            on { withColor(eq(ConsoleColor.White), any()) } doAnswer {
                val printStatements = it.getArgument<Console.() -> Unit>(1)
                printStatements(whiteConsole)
            }
        }

        val logger = EventLogger(console)
        val container = Container("the-cool-container", "/build/dir/doesnt/matter")

        on("receiving an 'image build started' event") {
            logger.imageBuildStarted(container)

            it("prints a message to the output") {
                inOrder(whiteConsole) {
                    verify(whiteConsole).print("Building ")
                    verify(whiteConsole).printBold("the-cool-container")
                    verify(whiteConsole).println("...")
                }
            }
        }

        on("receiving a 'command started' event with an explicit command") {
            logger.commandStarted(container, "do-stuff.sh")

            it("prints a message to the output") {
                inOrder(whiteConsole) {
                    verify(whiteConsole).print("Running ")
                    verify(whiteConsole).printBold("do-stuff.sh")
                    verify(whiteConsole).print(" in ")
                    verify(whiteConsole).printBold("the-cool-container")
                    verify(whiteConsole).println("...")
                }
            }
        }

        on("receiving a 'command started' event with no explicit command") {
            logger.commandStarted(container, null)

            it("prints a message to the output") {
                inOrder(whiteConsole) {
                    verify(whiteConsole).print("Running ")
                    verify(whiteConsole).printBold("the-cool-container")
                    verify(whiteConsole).println("...")
                }
            }
        }
    }
})
