package decompose

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object ConsoleSpec : Spek({
    val ESC = "\u001B"
    val whiteText = "$ESC[37m"
    val redText = "$ESC[31m"
    val boldText = "$ESC[1m"
    val reset = "$ESC[0m"

    describe("a console") {
        val output = ByteArrayOutputStream()
        val console = Console(PrintStream(output))

        beforeEachTest {
            output.reset()
        }

        on("printing text") {
            console.print("This is some text")

            it("writes the text directly to the output") {
                assertThat(output.toString(), equalTo("This is some text"))
            }
        }

        on("printing a line of text") {
            console.println("This is some text")

            it("writes the text directly to the output") {
                assertThat(output.toString(), equalTo("This is some text\n"))
            }
        }

        on("printing coloured text") {
            console.withColor(ConsoleColor.White) {
                print("the white text")
            }

            it("writes the text to the output with the appropriate escape codes") {
                assertThat(output.toString(), equalTo("${whiteText}the white text$reset"))
            }
        }

        on("nesting coloured text") {
            console.withColor(ConsoleColor.White) {
                println("white")

                withColor(ConsoleColor.Red) {
                    println("red")
                }

                println("more white")
            }

            it("writes the text to the output with the appropriate escape codes") {
                assertThat(output.toString(), equalTo("${whiteText}white\n${reset}${redText}red\n${reset}${whiteText}more white\n$reset"))
            }
        }

        on("nesting coloured text of the same colour") {
            console.withColor(ConsoleColor.White) {
                println("white 1")

                withColor(ConsoleColor.White) {
                    println("white 2")
                }

                println("white 3")
            }

            it("writes the text to the output with the appropriate escape codes") {
                assertThat(output.toString(), equalTo("${whiteText}white 1\nwhite 2\nwhite 3\n$reset"))
            }
        }

        on("printing bold text using a lambda") {
            console.inBold {
                print("the bold text")
            }

            it("writes the text to the output with the appropriate escape codes") {
                assertThat(output.toString(), equalTo("${boldText}the bold text$reset"))
            }
        }

        on("printing bold text from a string") {
            console.printBold("the bold text")

            it("writes the text to the output with the appropriate escape codes") {
                assertThat(output.toString(), equalTo("${boldText}the bold text$reset"))
            }
        }

        on("nesting bold text inside bold text") {
            console.inBold {
                inBold {
                    print("the bold text")
                }
            }

            it("writes the text to the output with the appropriate escape codes") {
                assertThat(output.toString(), equalTo("${boldText}the bold text$reset"))
            }
        }

        on("nesting bold text inside coloured text") {
            console.withColor(ConsoleColor.Red) {
                print("red")
                inBold {
                    print("bold")
                }
                print("more red")
            }

            it("writes the text to the output with the appropriate escape codes") {
                assertThat(output.toString(), equalTo("${redText}red${boldText}bold${reset}${redText}more red$reset"))
            }
        }

        on("nesting coloured text inside bold text") {
            console.inBold {
                print("bold")
                withColor(ConsoleColor.Red) {
                    print("red")
                }
                print("more bold")
            }

            it("writes the text to the output with the appropriate escape codes") {
                assertThat(output.toString(), equalTo("${boldText}bold${redText}red${reset}${boldText}more bold$reset"))
            }
        }

        on("nested coloured text printing inside bold text") {
            console.inBold {
                print("bold")

                withColor(ConsoleColor.White) {
                    print("white")

                    withColor(ConsoleColor.Red) {
                        print("red")
                    }

                    print("more white")
                }

                print("more bold")
            }

            it("writes the text to the output with the appropriate escape codes") {
                val expected = "${boldText}bold${whiteText}white$reset" +
                        "${boldText}${redText}red$reset" +
                        "${boldText}${whiteText}more white$reset" +
                        "${boldText}more bold$reset"

                assertThat(output.toString(), equalTo(expected))
            }
        }
    }
})
