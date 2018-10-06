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
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TextRunSpec : Spek({
    describe("a series of formatted text elements") {
        describe("simplifying a series of text elements") {
            given("an empty set of elements") {
                val original = TextRun()

                on("simplifying it") {
                    val simplified = original.simplify()

                    it("returns an empty set of elements") {
                        assertThat(simplified, equalTo(original))
                    }
                }
            }

            given("a single text element") {
                val original = TextRun("hello world")

                on("simplifying it") {
                    val simplified = original.simplify()

                    it("returns that single text element") {
                        assertThat(simplified, equalTo(original))
                    }
                }
            }

            given("two text elements with different bold formatting") {
                val original = Text("Plain text") + Text.bold("Bold text")

                on("simplifying it") {
                    val simplified = original.simplify()

                    it("returns those two elements") {
                        assertThat(simplified, equalTo(original))
                    }
                }
            }

            given("two text elements with different colors") {
                val original = Text.green("Green text") + Text.blue("Blue text")

                on("simplifying it") {
                    val simplified = original.simplify()

                    it("returns those two elements") {
                        assertThat(simplified, equalTo(original))
                    }
                }
            }

            given("two text elements with the same bold formatting") {
                val original = Text.bold("First bold text") + Text.bold(" Second bold text")

                on("simplifying it") {
                    val simplified = original.simplify()

                    it("returns a single combined element") {
                        assertThat(simplified, equalTo(TextRun(Text.bold("First bold text Second bold text"))))
                    }
                }
            }

            given("two text elements with the same color") {
                val original = Text.red("First red text") + Text.red(" Second red text")

                on("simplifying it") {
                    val simplified = original.simplify()

                    it("returns a single combined element") {
                        assertThat(simplified, equalTo(TextRun(Text.red("First red text Second red text"))))
                    }
                }
            }

            given("three text elements with the first two sharing the same formatting") {
                val original = Text.red("First red text") + Text.red(" Second red text") + Text("Other text")

                on("simplifying it") {
                    val simplified = original.simplify()

                    it("combines the first two elements and leaves the third as-is") {
                        assertThat(simplified, equalTo(TextRun(Text.red("First red text Second red text"), Text("Other text"))))
                    }
                }
            }

            given("three text elements with the last two sharing the same formatting") {
                val original = Text("Other text") + Text.red("First red text") + Text.red(" Second red text")

                on("simplifying it") {
                    val simplified = original.simplify()

                    it("combines the last two elements and leaves the first as-is") {
                        assertThat(simplified, equalTo(TextRun(Text("Other text"), Text.red("First red text Second red text"))))
                    }
                }
            }

            given("three text elements with all three sharing the same formatting") {
                val original = Text.red("First red text") + Text.red(" Second red text") + Text.red(" Third red text")

                on("simplifying it") {
                    val simplified = original.simplify()

                    it("returns a single combined element") {
                        assertThat(simplified, equalTo(TextRun(Text.red("First red text Second red text Third red text"))))
                    }
                }
            }

            given("four text elements with the first two sharing the same formatting and the last two sharing a different set of formatting") {
                val original = Text.red("First red text") + Text.red(" Second red text") + Text("First unformatted text") + Text(" Second unformatted text")

                on("simplifying it") {
                    val simplified = original.simplify()

                    it("combines the first two elements and combines the last two elements") {
                        assertThat(simplified, equalTo(TextRun(Text.red("First red text Second red text"), Text("First unformatted text Second unformatted text"))))
                    }
                }
            }
        }

        describe("restricting text to a certain length") {
            val length = 10

            on("restricting text that is shorter than the width of the console") {
                val text = TextRun("123456789")

                it("returns all text") {
                    assertThat(text.limitToLength(length), equalTo(TextRun("123456789")))
                }
            }

            on("restricting text that is equal to the width of the console") {
                val text = TextRun("1234567890")

                it("returns all text") {
                    assertThat(text.limitToLength(length), equalTo(TextRun("1234567890")))
                }
            }

            on("restricting text made up of multiple segments that is equal to the width of the console") {
                val text = Text("123456") + Text.red("7890")

                it("returns all text") {
                    assertThat(text.limitToLength(length), equalTo(Text("123456") + Text.red("7890")))
                }
            }

            on("restricting text that is longer than the width of the console") {
                val text = TextRun("12345678901")

                it("returns as much text as possible, replacing the last three characters with ellipsis") {
                    assertThat(text.limitToLength(length), equalTo(TextRun("1234567...")))
                }
            }

            on("restricting text with multiple lines") {
                val text = TextRun("12345678901\n")

                it("throws an appropriate exception") {
                    assertThat({ text.limitToLength(length) }, throws<UnsupportedOperationException>(withMessage("Cannot restrict the length of text containing line breaks.")))
                }
            }

            on("restricting text that is shorter than the width of the console but would contain more control characters than the width of the console") {
                val text = TextRun(Text.red("abc123"))

                it("returns all text, including the control characters") {
                    assertThat(text.limitToLength(length), equalTo(TextRun(Text.red("abc123"))))
                }
            }

            on("restricting coloured text that is longer than the width of the console, with further coloured text afterwards") {
                val text = Text.red("12345678901") + Text.white("white")

                it("returns as much text as possible, replacing the last three characters with ellipsis, and does not include the redundant text element") {
                    assertThat(text.limitToLength(length), equalTo(TextRun(Text.red("1234567..."))))
                }
            }

            on("restricting text where the colour would change for the first character of the ellipsis") {
                val text = Text.red("abc1234") + Text.white("wwww")

                it("returns the text, with the ellipsis taking the colour of the text it appears next to") {
                    assertThat(text.limitToLength(length), equalTo(TextRun(Text.red("abc1234..."))))
                }
            }

            on("restricting text where the limit is the same as the length of the ellipsis") {
                val text = TextRun(Text.red("This doesn't matter"))

                it("returns just the ellipsis with the same formatting as the text") {
                    assertThat(text.limitToLength(3), equalTo(TextRun(Text.red("..."))))
                }
            }

            on("restricting text where the limit is the less than the length of the ellipsis") {
                val text = TextRun(Text.red("This doesn't matter"))

                it("returns just the ellipsis with the same formatting as the text") {
                    assertThat(text.limitToLength(2), equalTo(TextRun(Text.red(".."))))
                }
            }

            on("restricting text where the limit is zero") {
                val text = TextRun(Text.red("This doesn't matter"))

                it("returns an empty TextRun") {
                    assertThat(text.limitToLength(0), equalTo(TextRun()))
                }
            }

            on("restricting an empty set of text") {
                val text = TextRun()

                it("returns an empty TextRun") {
                    assertThat(text.limitToLength(length), equalTo(TextRun()))
                }
            }
        }
    }
})
