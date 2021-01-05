/*
   Copyright 2017-2021 Charles Korn.

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

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayInputStream

object PromptSpec : Spek({
    describe("a prompt") {
        val console by createForEachTest { mock<Console>() }

        describe("asking yes or no questions") {
            given("the user immediately presses enter") {
                val stdin by createForEachTest { ByteArrayInputStream("\n".toByteArray(Charsets.UTF_8)) }
                val prompt by createForEachTest { Prompt(console, stdin) }
                val result by runForEachTest { prompt.askYesNoQuestion("Are you sure you want to do that?") }

                it("prints the question to the console in the expected format") {
                    verify(console).print("Are you sure you want to do that? (Y/n) ")
                }

                it("returns a 'yes' answer") {
                    assertThat(result, equalTo(YesNoAnswer.Yes))
                }
            }

            setOf("yes", "YES", "y", "Y", "YeS").forEach { answer ->
                given("the user immediately enters '$answer'") {
                    val stdin by createForEachTest { ByteArrayInputStream("$answer\n".toByteArray(Charsets.UTF_8)) }
                    val prompt by createForEachTest { Prompt(console, stdin) }
                    val result by runForEachTest { prompt.askYesNoQuestion("Are you sure you want to do that?") }

                    it("prints the question to the console in the expected format") {
                        verify(console).print("Are you sure you want to do that? (Y/n) ")
                    }

                    it("returns a 'yes' answer") {
                        assertThat(result, equalTo(YesNoAnswer.Yes))
                    }
                }
            }

            setOf("no", "NO", "n", "N", "No").forEach { answer ->
                given("the user immediately enters '$answer'") {
                    val stdin by createForEachTest { ByteArrayInputStream("$answer\n".toByteArray(Charsets.UTF_8)) }
                    val prompt by createForEachTest { Prompt(console, stdin) }
                    val result by runForEachTest { prompt.askYesNoQuestion("Are you sure you want to do that?") }

                    it("prints the question to the console in the expected format") {
                        verify(console).print("Are you sure you want to do that? (Y/n) ")
                    }

                    it("returns a 'no' answer") {
                        assertThat(result, equalTo(YesNoAnswer.No))
                    }
                }
            }

            given("the user enters an invalid answer, then answers 'yes'") {
                val stdin by createForEachTest { ByteArrayInputStream("blah\nyes\n".toByteArray(Charsets.UTF_8)) }
                val prompt by createForEachTest { Prompt(console, stdin) }
                val result by runForEachTest { prompt.askYesNoQuestion("Are you sure you want to do that?") }

                it("prints the question to the console in the expected format, and then prompts the user to answer again after they enter an invalid response") {
                    inOrder(console) {
                        verify(console).print("Are you sure you want to do that? (Y/n) ")
                        verify(console).print("Please enter 'y' or 'n': ")
                    }
                }

                it("returns a 'yes' answer") {
                    assertThat(result, equalTo(YesNoAnswer.Yes))
                }
            }

            given("the user enters an invalid answer, then answers 'no'") {
                val stdin by createForEachTest { ByteArrayInputStream("blah\nno\n".toByteArray(Charsets.UTF_8)) }
                val prompt by createForEachTest { Prompt(console, stdin) }
                val result by runForEachTest { prompt.askYesNoQuestion("Are you sure you want to do that?") }

                it("prints the question to the console in the expected format, and then prompts the user to answer again after they enter an invalid response") {
                    inOrder(console) {
                        verify(console).print("Are you sure you want to do that? (Y/n) ")
                        verify(console).print("Please enter 'y' or 'n': ")
                    }
                }

                it("returns a 'no' answer") {
                    assertThat(result, equalTo(YesNoAnswer.No))
                }
            }
        }
    }
})
