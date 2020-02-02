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
                assertThat("".breakAt(20), equalTo(""))
            }
        }

        given("a string shorter than the maximum width") {
            it("returns the original string") {
                assertThat("blah".breakAt(20), equalTo("blah"))
            }
        }

        given("a string with the same length as the maximum width") {
            it("returns the original string") {
                assertThat("blah".breakAt(4), equalTo("blah"))
            }
        }

        given("a string longer than the maximum width with only one word") {
            it("returns the original string") {
                assertThat("blah".breakAt(3), equalTo("blah"))
            }
        }

        given("a string longer than the maximum width with only one word") {
            it("returns the original string") {
                assertThat("blah foo".breakAt(3).lines(), equalTo(listOf("blah", "foo")))
            }
        }

        given("a string longer than the maximum width with two words where the first word fits within the maximum width") {
            it("moves the second word to a new line") {
                assertThat("the foo".breakAt(4).lines(), equalTo(listOf("the", "foo")))
            }
        }

        given("a string longer than the maximum width with two words where the first word is exactly the maximum width") {
            it("moves the second word to a new line") {
                assertThat("the foo".breakAt(3).lines(), equalTo(listOf("the", "foo")))
            }
        }

        given("a string longer than the maximum width with many words that must be broken into multiple lines to fit within the maximum width") {
            it("moves the words to new lines as required to fit in the maximum line width") {
                assertThat("abc def ghi".breakAt(4).lines(), equalTo(listOf("abc", "def", "ghi")))
            }
        }

        given("a string longer than the maximum width with multiple words that fit within the maximum width on the first line") {
            it("keeps as many words as possible on the first line") {
                assertThat("a bc def".breakAt(4).lines(), equalTo(listOf("a bc", "def")))
            }
        }

        given("a string longer than the maximum width with multiple words that fit within the maximum width on the second line") {
            it("keeps as many words as possible on the first and second lines") {
                assertThat("a bc de f".breakAt(4).lines(), equalTo(listOf("a bc", "de f")))
            }
        }

        given("a string longer than the maximum width with a word longer than the maximum width") {
            it("puts the word longer than the maximum width on its own line") {
                assertThat("abc defgh ijk".breakAt(4).lines(), equalTo(listOf("abc", "defgh", "ijk")))
            }
        }
    }
})
