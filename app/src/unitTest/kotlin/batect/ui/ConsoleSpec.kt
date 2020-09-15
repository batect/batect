/*
   Copyright 2017-2020 Charles Korn.

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

import batect.os.ConsoleDimensions
import batect.os.Dimensions
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.on
import batect.testutils.withMessage
import batect.testutils.withPlatformSpecificLineSeparator
import batect.ui.text.Text
import batect.ui.text.TextRun
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
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
            val output by createForEachTest { ByteArrayOutputStream() }
            val console by createForEachTest { Console(PrintStream(output), enableComplexOutput = true, consoleDimensions = mock()) }

            on("printing text") {
                beforeEachTest { console.print("This is some text") }

                it("writes the text directly to the output") {
                    assertThat(output.toString(), equalTo("This is some text"))
                }
            }

            on("printing a line of text") {
                beforeEachTest { console.println("This is some text") }

                it("writes the text directly to the output") {
                    assertThat(output.toString(), equalTo("This is some text\n".withPlatformSpecificLineSeparator()))
                }
            }

            on("printing a blank line of text") {
                beforeEachTest { console.println() }

                it("writes a blank line directly to the output") {
                    assertThat(output.toString(), equalTo("\n".withPlatformSpecificLineSeparator()))
                }
            }

            on("printing unformatted text") {
                beforeEachTest { console.print(Text("Hello")) }

                it("writes that text directly to the output") {
                    assertThat(output.toString(), equalTo("Hello"))
                }
            }

            on("printing unformatted text, ending with a new line") {
                beforeEachTest { console.println(Text("Hello")) }

                it("writes that text directly to the output") {
                    assertThat(output.toString(), equalTo("Hello\n".withPlatformSpecificLineSeparator()))
                }
            }

            on("printing some coloured text") {
                beforeEachTest { console.print(Text.white("the white text")) }

                it("writes the text to the output with the appropriate escape codes") {
                    assertThat(output.toString(), equalTo("${whiteText}the white text$reset"))
                }
            }

            on("printing some coloured text, ending with a new line") {
                beforeEachTest { console.println(Text.white("the white text")) }

                it("writes the text to the output with the appropriate escape codes") {
                    assertThat(output.toString(), equalTo("${whiteText}the white text$reset\n".withPlatformSpecificLineSeparator()))
                }
            }

            on("printing some bold text") {
                beforeEachTest { console.print(Text.bold("the bold text")) }

                it("writes the text to the output with the appropriate escape codes") {
                    assertThat(output.toString(), equalTo("${boldText}the bold text$reset"))
                }
            }

            on("printing some bold text, ending with a new line") {
                beforeEachTest { console.println(Text.bold("the bold text")) }

                it("writes the text to the output with the appropriate escape codes") {
                    assertThat(output.toString(), equalTo("${boldText}the bold text$reset\n".withPlatformSpecificLineSeparator()))
                }
            }

            on("printing bold and coloured text") {
                beforeEachTest { console.print(Text("bold and red", ConsoleColor.Red, true)) }

                it("writes the text to the output with the appropriate escape codes") {
                    assertThat(output.toString(), equalTo("${redText}${boldText}bold and red$reset"))
                }
            }

            on("printing bold and coloured text, ending with a new line") {
                beforeEachTest { console.println(Text("bold and red", ConsoleColor.Red, true)) }

                it("writes the text to the output with the appropriate escape codes") {
                    assertThat(output.toString(), equalTo("${redText}${boldText}bold and red$reset\n".withPlatformSpecificLineSeparator()))
                }
            }

            on("printing a series of unformatted text elements") {
                beforeEachTest { console.print(Text("Hello") + Text(" World")) }

                it("writes that text directly to the output") {
                    assertThat(output.toString(), equalTo("Hello World"))
                }
            }

            on("printing a series of unformatted text elements, ending with a new line") {
                beforeEachTest { console.println(Text("Hello") + Text(" World")) }

                it("writes that text directly to the output") {
                    assertThat(output.toString(), equalTo("Hello World\n".withPlatformSpecificLineSeparator()))
                }
            }

            on("printing a series of coloured text elements") {
                beforeEachTest { console.print(Text.red("red") + Text.white("white")) }

                it("writes the text to the output with the appropriate escape codes") {
                    assertThat(output.toString(), equalTo("${redText}red${reset}${whiteText}white$reset"))
                }
            }

            on("printing a series of coloured text elements, ending with a new line") {
                beforeEachTest { console.println(Text.red("red") + Text.white("white")) }

                it("writes the text to the output with the appropriate escape codes") {
                    assertThat(output.toString(), equalTo("${redText}red${reset}${whiteText}white$reset\n".withPlatformSpecificLineSeparator()))
                }
            }

            on("printing a series of bold and non-bold text elements") {
                beforeEachTest { console.print(Text.bold("bold") + Text("not")) }

                it("writes the text to the output with the appropriate escape codes") {
                    assertThat(output.toString(), equalTo("${boldText}bold${reset}not"))
                }
            }

            on("printing a series of bold and non-bold text elements, ending with a new line") {
                beforeEachTest { console.println(Text.bold("bold") + Text("not")) }

                it("writes the text to the output with the appropriate escape codes") {
                    assertThat(output.toString(), equalTo("${boldText}bold${reset}not\n".withPlatformSpecificLineSeparator()))
                }
            }

            on("printing some bold and coloured text elements") {
                beforeEachTest { console.print(Text.bold(Text.red("red") + Text.white("white"))) }

                it("writes the text to the output with the appropriate escape codes") {
                    assertThat(output.toString(), equalTo("${redText}${boldText}red${reset}${whiteText}${boldText}white$reset"))
                }
            }

            on("printing some bold and coloured text elements, ending with a new line") {
                beforeEachTest { console.println(Text.bold(Text.red("red") + Text.white("white"))) }

                it("writes the text to the output with the appropriate escape codes") {
                    assertThat(output.toString(), equalTo("${redText}${boldText}red${reset}${whiteText}${boldText}white$reset\n".withPlatformSpecificLineSeparator()))
                }
            }

            describe("moving the cursor up") {
                on("moving the cursor up one line") {
                    beforeEachTest { console.moveCursorUp() }

                    it("writes the appropriate escape code to the output") {
                        assertThat(output.toString(), equalTo("$ESC[1A"))
                    }
                }

                on("moving the cursor up multiple lines") {
                    beforeEachTest { console.moveCursorUp(23) }

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
                    beforeEachTest { console.moveCursorDown() }

                    it("writes the appropriate escape code to the output") {
                        assertThat(output.toString(), equalTo("$ESC[1B"))
                    }
                }

                on("moving the cursor down multiple lines") {
                    beforeEachTest { console.moveCursorDown(23) }

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
                beforeEachTest { console.clearCurrentLine() }

                it("writes the appropriate escape code sequence to the output") {
                    assertThat(output.toString(), equalTo("\r$ESC[K"))
                }
            }

            on("moving to the start of the current line") {
                beforeEachTest { console.moveCursorToStartOfLine() }

                it("writes the appropriate escape code to the output") {
                    assertThat(output.toString(), equalTo("\r"))
                }
            }
        }

        describe("when complex output is disabled") {
            val output by createForEachTest { ByteArrayOutputStream() }
            val console by createForEachTest { Console(PrintStream(output), enableComplexOutput = false, consoleDimensions = mock()) }

            on("printing text") {
                beforeEachTest { console.print("This is some text") }

                it("writes the text directly to the output") {
                    assertThat(output.toString(), equalTo("This is some text"))
                }
            }

            on("printing a line of text") {
                beforeEachTest { console.println("This is some text") }

                it("writes the text directly to the output") {
                    assertThat(output.toString(), equalTo("This is some text\n".withPlatformSpecificLineSeparator()))
                }
            }

            on("printing a blank line of text") {
                beforeEachTest { console.println() }

                it("writes a blank line directly to the output") {
                    assertThat(output.toString(), equalTo("\n".withPlatformSpecificLineSeparator()))
                }
            }

            on("printing coloured text") {
                beforeEachTest { console.print(Text.white("the white text")) }

                it("writes the text to the output without any escape codes") {
                    assertThat(output.toString(), equalTo("the white text"))
                }
            }

            on("printing bold text") {
                beforeEachTest { console.print(Text.bold("the bold text")) }

                it("writes the text to the output without any escape codes") {
                    assertThat(output.toString(), equalTo("the bold text"))
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
                val consoleDimensions by createForEachTest {
                    mock<ConsoleDimensions> {
                        on { current } doReturn null as Dimensions?
                    }
                }

                val output by createForEachTest { ByteArrayOutputStream() }
                val console by createForEachTest { Console(PrintStream(output), true, consoleDimensions) }

                on("printing text") {
                    beforeEachTest { console.printLineLimitedToConsoleWidth(TextRun("This is some text")) }

                    it("prints all text") {
                        assertThat(output.toString(), equalTo("This is some text\n".withPlatformSpecificLineSeparator()))
                    }
                }
            }

            given("the console dimensions are available") {
                val consoleDimensions by createForEachTest {
                    mock<ConsoleDimensions> {
                        on { current } doReturn Dimensions(40, 10)
                    }
                }

                val output by createForEachTest { ByteArrayOutputStream() }
                val console by createForEachTest { Console(PrintStream(output), true, consoleDimensions) }

                on("printing text") {
                    val text = mock<TextRun> {
                        on { limitToLength(10) } doReturn TextRun("This is some shortened text")
                    }

                    beforeEachTest { console.printLineLimitedToConsoleWidth(text) }

                    it("prints the shortened text") {
                        assertThat(output.toString(), equalTo("This is some shortened text\n".withPlatformSpecificLineSeparator()))
                    }
                }
            }
        }
    }
})
