/*
   Copyright 2017-2018 Charles Korn.

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

package batect.ui.text

import batect.testutils.equalTo
import batect.ui.ConsoleColor
import batect.ui.text.Text.Companion.black
import batect.ui.text.Text.Companion.blue
import batect.ui.text.Text.Companion.cyan
import batect.ui.text.Text.Companion.green
import batect.ui.text.Text.Companion.magenta
import batect.ui.text.Text.Companion.red
import batect.ui.text.Text.Companion.white
import batect.ui.text.Text.Companion.yellow
import com.natpryce.hamkrest.assertion.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TextSpec : Spek({
    describe("some formatted text") {
        describe("creating bold text") {
            on("creating bold text from a string") {
                val text = Text.bold("Some text")

                it("returns a bold text element") {
                    assertThat(text, equalTo(Text("Some text", bold = true)))
                }
            }

            on("creating bold text from a series of unformatted text elements") {
                val text = Text.bold(Text("Some") + Text("text"))

                it("returns a series of bold text elements") {
                    assertThat(text, equalTo(Text("Some", bold = true) + Text("text", bold = true)))
                }
            }

            on("creating bold text from a series of text elements, some of which are already bold") {
                val text = Text.bold(Text("Some") + Text.bold("text"))

                it("returns a series of bold text elements") {
                    assertThat(text, equalTo(Text("Some", bold = true) + Text("text", bold = true)))
                }
            }

            on("creating bold text from a series of text elements, some of which are coloured") {
                val text = Text.bold(Text("Some") + Text.red("text"))

                it("returns a series of bold text elements") {
                    assertThat(text, equalTo(Text("Some", bold = true) + Text("text", bold = true, color = ConsoleColor.Red)))
                }
            }

            on("creating bold text from a series of text elements, some of which already have formatting applied") {
                val text = Text.bold(Text("Some") + Text.bold("text") + Text("not bold", bold = false) + Text.red("red text"))

                it("returns an instance with the expected formatting properties, not overriding any existing not-bold text") {
                    assertThat(text, equalTo(Text("Some", bold = true) + Text("text", bold = true) + Text("not bold", bold = false) + Text("red text", bold = true, color = ConsoleColor.Red)))
                }
            }
        }

        describe("creating coloured text") {
            mapOf<ConsoleColor, (String) -> Text>(
                ConsoleColor.Black to ::black,
                ConsoleColor.Red to ::red,
                ConsoleColor.Green to ::green,
                ConsoleColor.Yellow to ::yellow,
                ConsoleColor.Blue to ::blue,
                ConsoleColor.Magenta to ::magenta,
                ConsoleColor.Cyan to ::cyan,
                ConsoleColor.White to ::white
            ).forEach { color, producer ->
                val colorName = color.name.toLowerCase()

                on("creating $colorName text from a string") {
                    val text = producer("Some text")

                    it("returns a $colorName text element") {
                        assertThat(text, equalTo(Text("Some text", color = color)))
                    }
                }
            }

            mapOf<ConsoleColor, (TextRun) -> TextRun>(
                ConsoleColor.Black to ::black,
                ConsoleColor.Red to ::red,
                ConsoleColor.Green to ::green,
                ConsoleColor.Yellow to ::yellow,
                ConsoleColor.Blue to ::blue,
                ConsoleColor.Magenta to ::magenta,
                ConsoleColor.Cyan to ::cyan,
                ConsoleColor.White to ::white
            ).forEach { color, producer ->
                val colorName = color.name.toLowerCase()
                val anotherColor = ConsoleColor.values().toList().filterNot { it == color }.first()

                on("creating $colorName text from a series of unformatted text elements") {
                    val text = producer(Text("Some") + Text("text"))

                    it("returns a series of $colorName text elements") {
                        assertThat(text, equalTo(Text("Some", color = color) + Text("text", color = color)))
                    }
                }

                on("creating $colorName text from a series of text elements, each of which is already $colorName") {
                    val text = producer(Text("Some", color = color) + Text("text", color = color))

                    it("returns a series of $colorName text elements") {
                        assertThat(text, equalTo(Text("Some", color = color) + Text("text", color = color)))
                    }
                }

                on("creating $colorName text from a series of text elements, each of which is already another colour") {
                    val text = producer(Text("Some", color = anotherColor) + Text("text", color = anotherColor))

                    it("returns a series of text elements with the original colour preserved") {
                        assertThat(text, equalTo(Text("Some", color = anotherColor) + Text("text", color = anotherColor)))
                    }
                }

                on("creating $colorName text from a series of text elements, some of which are bold") {
                    val text = producer(Text("Some") + Text.bold("text"))

                    it("returns a series of $colorName text elements, with the bold text preserved") {
                        assertThat(text, equalTo(Text("Some", color = color) + Text("text", color = color, bold = true)))
                    }
                }

                on("creating $colorName text from a series of text elements, some of which already have formatting applied") {
                    val text = producer(Text("Some") + Text("text", color = color) + Text("special color", color = anotherColor) + Text.bold("bold text"))

                    it("returns an instance with the expected formatting properties, not overriding any existing non-$colorName text") {
                        assertThat(text, equalTo(Text("Some", color = color) + Text("text", color = color) + Text("special color", color = anotherColor) + Text("bold text", bold = true, color = color)))
                    }
                }
            }
        }

        describe("combining text elements together") {
            val text1 = Text("First")
            val text2 = Text("Second")
            val expected = TextRun(text1, text2)

            on("combining two elements") {
                val result = text1 + text2

                it("converts the two elements to a single text run") {
                    assertThat(result, equalTo(expected))
                }
            }

            on("combining two elements, the first of which is a text run") {
                val result = TextRun(text1) + text2

                it("converts the two elements to a single text run") {
                    assertThat(result, equalTo(expected))
                }
            }

            on("combining two elements, the second of which is a text run") {
                val result = text1 + TextRun(text2)

                it("converts the two elements to a single text run") {
                    assertThat(result, equalTo(expected))
                }
            }

            on("combining two elements, both of which are text runs") {
                val result = TextRun(text1) + TextRun(text2)

                it("converts the two elements to a single text run") {
                    assertThat(result, equalTo(expected))
                }
            }
        }
    }
})
