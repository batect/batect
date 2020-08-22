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

package batect.utils

import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object StringExtensionsSpec : Spek({
    describe("splitting a string over multiple lines of a certain length") {
        given("an empty string") {
            it("returns an empty string") {
                assertThat("".breakAt(20), equalTo(listOf("")))
            }
        }

        given("a string shorter than the maximum width") {
            it("returns the original string") {
                assertThat("blah".breakAt(20), equalTo(listOf("blah")))
            }
        }

        given("a string with the same length as the maximum width") {
            it("returns the original string") {
                assertThat("blah".breakAt(4), equalTo(listOf("blah")))
            }
        }

        given("a string longer than the maximum width with only one word") {
            it("returns the original string") {
                assertThat("blah".breakAt(3), equalTo(listOf("blah")))
            }
        }

        given("a string longer than the maximum width with two words where the first word is longer than the maximum width") {
            it("moves the second word to a new line") {
                assertThat("blah foo".breakAt(3), equalTo(listOf("blah", "foo")))
            }
        }

        given("a string longer than the maximum width with two words where the total length is one character longer than the maximum width") {
            it("moves the second word to a new line") {
                assertThat("blah foo".breakAt(7), equalTo(listOf("blah", "foo")))
            }
        }

        given("a string longer than the maximum width with two words where the first word fits within the maximum width") {
            it("moves the second word to a new line") {
                assertThat("the foo".breakAt(4), equalTo(listOf("the", "foo")))
            }
        }

        given("a string longer than the maximum width with two words where the first word is exactly the maximum width") {
            it("moves the second word to a new line") {
                assertThat("the foo".breakAt(3), equalTo(listOf("the", "foo")))
            }
        }

        given("a string longer than the maximum width with many words that must be broken into multiple lines to fit within the maximum width") {
            it("moves the words to new lines as required to fit in the maximum line width") {
                assertThat("abc def ghi".breakAt(4), equalTo(listOf("abc", "def", "ghi")))
            }
        }

        given("a string longer than the maximum width with multiple words that fit within the maximum width on the first line") {
            it("keeps as many words as possible on the first line") {
                assertThat("a bc def".breakAt(4), equalTo(listOf("a bc", "def")))
            }
        }

        given("a string longer than the maximum width with multiple words that fit within the maximum width on the second line") {
            it("keeps as many words as possible on the first and second lines") {
                assertThat("a bc de f".breakAt(4), equalTo(listOf("a bc", "de f")))
            }
        }

        given("a string longer than the maximum width with a word longer than the maximum width") {
            it("puts the word longer than the maximum width on its own line") {
                assertThat("abc defgh ijk".breakAt(4), equalTo(listOf("abc", "defgh", "ijk")))
            }
        }
    }

    describe("producing a human-readable list of a list of strings") {
        given("an empty list") {
            it("returns an empty string") {
                assertThat(emptyList<String>().asHumanReadableList(), equalTo(""))
            }
        }

        given("a list with a single item") {
            it("returns just that item") {
                assertThat(listOf("item 1").asHumanReadableList(), equalTo("item 1"))
            }
        }

        given("a list with two items") {
            it("returns both items, separated by 'and'") {
                assertThat(listOf("item 1", "item 2").asHumanReadableList(), equalTo("item 1 and item 2"))
            }
        }

        given("a list with three items") {
            it("returns all items, with the first pair separated by a comma, and the second pair by 'and'") {
                assertThat(listOf("item 1", "item 2", "item 3").asHumanReadableList(), equalTo("item 1, item 2 and item 3"))
            }
        }

        given("a list with many items") {
            it("returns all items, with all but the last pair separated by a comma, and the last pair by 'and'") {
                assertThat(listOf("item 1", "item 2", "item 3", "item 4", "item 5").asHumanReadableList(), equalTo("item 1, item 2, item 3, item 4 and item 5"))
            }

            it("returns all items, with all but the last pair separated by a comma, and the last pair by the provided custom conjunction") {
                assertThat(listOf("item 1", "item 2", "item 3", "item 4", "item 5").asHumanReadableList("or"), equalTo("item 1, item 2, item 3, item 4 or item 5"))
            }
        }
    }
})
