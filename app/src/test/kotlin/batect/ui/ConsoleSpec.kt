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
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
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
        describe("when complex output is enabled") {
            val output = ByteArrayOutputStream()
            val console = Console(PrintStream(output), enableComplexOutput = true, consoleInfo = mock())

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

        describe("when complex output is disabled") {
            val output = ByteArrayOutputStream()
            val console = Console(PrintStream(output), enableComplexOutput = false, consoleInfo = mock())

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

                it("writes the text to the output without any escape codes") {
                    assertThat(output.toString(), equalTo("the white text"))
                }
            }

            on("printing bold text using a lambda") {
                console.inBold {
                    print("the bold text")
                }

                it("writes the text to the output without any escape codes") {
                    assertThat(output.toString(), equalTo("the bold text"))
                }
            }

            on("printing bold text from a string") {
                console.printBold("the bold text")

                it("writes the text to the output without any escape codes") {
                    assertThat(output.toString(), equalTo("the bold text"))
                }
            }

            on("nesting bold text inside coloured text") {
                console.withColor(ConsoleColor.Red) {
                    print("red ")
                    inBold {
                        print("bold")
                    }
                    print(" more red")
                }

                it("writes the text to the output without any escape codes") {
                    assertThat(output.toString(), equalTo("red bold more red"))
                }
            }

            on("nesting coloured text inside bold text") {
                console.inBold {
                    print("bold ")
                    withColor(ConsoleColor.Red) {
                        print("red")
                    }
                    print(" more bold")
                }

                it("writes the text to the output without any escape codes") {
                    assertThat(output.toString(), equalTo("bold red more bold"))
                }
            }

            on("moving the cursor up") {
                it("throws an appropriate exception") {
                    assertThat({ console.moveCursorUp(1) }, throws<UnsupportedOperationException>(withMessage("Cannot move the cursor when complex output is disabled.")))
                }
            }

            on("moving the cursor down") {
                it("throws an appropriate exception") {
                    assertThat({ console.moveCursorDown(1) }, throws<UnsupportedOperationException>(withMessage("Cannot move the cursor when complex output is disabled.")))
                }
            }

            on("clearing the current line") {
                it("throws an appropriate exception") {
                    assertThat({ console.clearCurrentLine() }, throws<UnsupportedOperationException>(withMessage("Cannot clear the current line when complex output is disabled.")))
                }
            }

            on("moving to the start of the current line") {
                it("throws an appropriate exception") {
                    assertThat({ console.moveCursorToStartOfLine() }, throws<UnsupportedOperationException>(withMessage("Cannot move the cursor when complex output is disabled.")))
                }
            }
        }

        describe("printing text restricted to the width of the console") {
            given("the console dimensions are not available") {
                val consoleInfo = mock<ConsoleInfo> {
                    on { dimensions } doReturn null as Dimensions?
                }

                val output = ByteArrayOutputStream()
                val console = Console(PrintStream(output), true, consoleInfo)

                on("printing text") {
                    console.restrictToConsoleWidth {
                        print("This is some text")
                    }

                    it("prints all text") {
                        assertThat(output.toString(), equalTo("This is some text"))
                    }
                }
            }

            given("the console dimensions are available") {
                val consoleInfo = mock<ConsoleInfo> {
                    on { dimensions } doReturn Dimensions(40, 10)
                }

                val output = ByteArrayOutputStream()
                val console = Console(PrintStream(output), true, consoleInfo)

                beforeEachTest {
                    output.reset()
                }

                on("printing text that is shorter than the width of the console") {
                    console.restrictToConsoleWidth {
                        print("123456789")
                    }

                    it("prints all text") {
                        assertThat(output.toString(), equalTo("123456789"))
                    }
                }

                on("printing text that is equal to the width of the console") {
                    console.restrictToConsoleWidth {
                        print("1234567890")
                    }

                    it("prints all text") {
                        assertThat(output.toString(), equalTo("1234567890"))
                    }
                }

                on("printing text that is longer than the width of the console") {
                    console.restrictToConsoleWidth {
                        print("12345678901")
                    }

                    it("prints as much text as possible, replacing the last three characters with ellipsis") {
                        assertThat(output.toString(), equalTo("1234567..."))
                    }
                }

                on("printing text with multiple lines") {
                    it("throws an appropriate exception") {
                        assertThat({
                            console.restrictToConsoleWidth {
                                println("12345678901")
                            }
                        }, throws<UnsupportedOperationException>(withMessage("Cannot restrict the width of output containing line breaks.")))
                    }
                }

                on("printing text that is shorter than the width of the console but contains more control characters than the width of the console") {
                    console.restrictToConsoleWidth {
                        withColor(ConsoleColor.Red) {
                            print("abc123")
                        }
                    }

                    it("prints all text, including the control characters") {
                        assertThat(output.toString(), equalTo("${redText}abc123$reset"))
                    }
                }

                on("printing coloured text that is longer than the width of the console") {
                    console.restrictToConsoleWidth {
                        withColor(ConsoleColor.Red) {
                            print("12345678901")
                        }
                    }

                    it("prints as much text as possible, replacing the last three characters with ellipsis") {
                        assertThat(output.toString(), equalTo("${redText}1234567...$reset"))
                    }
                }

                on("printing coloured text that is longer than the width of the console, with further coloured text afterwards") {
                    console.restrictToConsoleWidth {
                        withColor(ConsoleColor.Red) {
                            print("12345678901")
                        }
                        withColor(ConsoleColor.White) {
                            print("white")
                        }
                    }

                    it("prints as much text as possible, replacing the last three characters with ellipsis, and does not include the redundant escape sequences") {
                        assertThat(output.toString(), equalTo("${redText}1234567...$reset"))
                    }
                }

                on("printing text where the colour would change for the first character of the ellipsis") {
                    console.restrictToConsoleWidth {
                        withColor(ConsoleColor.Red) {
                            print("abc1234")
                        }
                        withColor(ConsoleColor.White) {
                            print("wwww")
                        }
                    }

                    it("prints the text, with the ellipsis taking the colour of the text it appears next to") {
                        assertThat(output.toString(), equalTo("${redText}abc1234...$reset"))
                    }
                }

                on("printing coloured text from within a console that is already coloured") {
                    console.withColor(ConsoleColor.Red) {
                        print("red")

                        restrictToConsoleWidth {
                            withColor(ConsoleColor.White) {
                                print("white123456")
                            }
                        }

                        print("red")
                    }

                    it("prints the text, resetting the output to the original colour afterwards") {
                        assertThat(output.toString(), equalTo("${redText}red$reset${whiteText}white12...$reset${redText}red$reset"))
                    }
                }

                on("attempting to move the cursor up while restricted to the width of the console") {
                    it("throws an appropriate exception") {
                        assertThat({ console.restrictToConsoleWidth { moveCursorUp(1) } },
                            throws<UnsupportedOperationException>(withMessage("Cannot move the cursor while restricted to the width of the console.")))
                    }
                }

                on("attempting to move the cursor down while restricted to the width of the console") {
                    it("throws an appropriate exception") {
                        assertThat({ console.restrictToConsoleWidth { moveCursorDown(1) } },
                            throws<UnsupportedOperationException>(withMessage("Cannot move the cursor while restricted to the width of the console.")))
                    }
                }

                on("attempting to move the cursor to the start of the line while restricted to the width of the console") {
                    it("throws an appropriate exception") {
                        assertThat({ console.restrictToConsoleWidth { moveCursorToStartOfLine() } },
                            throws<UnsupportedOperationException>(withMessage("Cannot move the cursor while restricted to the width of the console.")))
                    }
                }

                on("attempting to clear the current line while restricted to the width of the console") {
                    it("throws an appropriate exception") {
                        assertThat({ console.restrictToConsoleWidth { clearCurrentLine() } },
                            throws<UnsupportedOperationException>(withMessage("Cannot clear the current line while restricted to the width of the console.")))
                    }
                }
            }
        }
    }
})
