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

package batect.ui

import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
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

        on("printing a blank line of text") {
            console.println()

            it("writes a blank line directly to the output") {
                assertThat(output.toString(), equalTo("\n"))
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

        describe("moving the cursor up") {
            on("moving the cursor up one line") {
                console.moveCursorUp()

                it("writes the appropriate escape code to the output") {
                    assertThat(output.toString(), equalTo("$ESC[1A"))
                }
            }

            on("moving the cursor up multiple lines") {
                console.moveCursorUp(23)

                it("writes the appropriate escape code to the output") {
                    assertThat(output.toString(), equalTo("$ESC[23A"))
                }
            }

            on("attempting to move the cursor up zero lines") {
                it("throws an appropriate exception") {
                    assertThat({ console.moveCursorUp(0) }, throws<IllegalArgumentException>(withMessage("Number of lines must be positive.")))
                }
            }

            on("attempting to move the cursor up a negative number of lines") {
                it("throws an appropriate exception") {
                    assertThat({ console.moveCursorUp(-1) }, throws<IllegalArgumentException>(withMessage("Number of lines must be positive.")))
                }
            }
        }

        describe("moving the cursor down") {
            on("moving the cursor down one line") {
                console.moveCursorDown()

                it("writes the appropriate escape code to the output") {
                    assertThat(output.toString(), equalTo("$ESC[1B"))
                }
            }

            on("moving the cursor down multiple lines") {
                console.moveCursorDown(23)

                it("writes the appropriate escape code to the output") {
                    assertThat(output.toString(), equalTo("$ESC[23B"))
                }
            }

            on("attempting to move the cursor down zero lines") {
                it("throws an appropriate exception") {
                    assertThat({ console.moveCursorDown(0) }, throws<IllegalArgumentException>(withMessage("Number of lines must be positive.")))
                }
            }

            on("attempting to move the cursor down a negative number of lines") {
                it("throws an appropriate exception") {
                    assertThat({ console.moveCursorDown(-1) }, throws<IllegalArgumentException>(withMessage("Number of lines must be positive.")))
                }
            }
        }

        on("clearing the current line") {
            console.clearCurrentLine()

            it("writes the appropriate escape code sequence to the output") {
                assertThat(output.toString(), equalTo("\r$ESC[K"))
            }
        }

        on("moving to the start of the current line") {
            console.moveCursorToStartOfLine()

            it("writes the appropriate escape code to the output") {
                assertThat(output.toString(), equalTo("\r"))
            }
        }
    }
})
