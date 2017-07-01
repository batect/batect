package decompose

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
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

        on("receiving an 'image build starting' event") {
            logger.imageBuildStarting(container)

            it("prints a message to the output") {
                inOrder(whiteConsole) {
                    verify(whiteConsole).print("Building ")
                    verify(whiteConsole).printBold("the-cool-container")
                    verify(whiteConsole).println("...")
                }
            }
        }

        on("receiving a 'command starting' event with an explicit command") {
            logger.commandStarting(container, "do-stuff.sh")

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

        on("receiving a 'command starting' event with no explicit command") {
            logger.commandStarting(container, null)

            it("prints a message to the output") {
                inOrder(whiteConsole) {
                    verify(whiteConsole).print("Running ")
                    verify(whiteConsole).printBold("the-cool-container")
                    verify(whiteConsole).println("...")
                }
            }
        }

        on("receiving a 'dependency starting' event") {
            logger.dependencyStarting(container)

            it("prints a message to the output") {
                inOrder(whiteConsole) {
                    verify(whiteConsole).print("Starting dependency ")
                    verify(whiteConsole).printBold("the-cool-container")
                    verify(whiteConsole).println("...")
                }
            }
        }
    }
})
