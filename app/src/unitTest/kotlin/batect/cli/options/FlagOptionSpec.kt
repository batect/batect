/*
   Copyright 2017-2019 Charles Korn.

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

package batect.cli.options

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.on
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object FlagOptionSpec : Spek({
    describe("a flag option") {
        val option by createForEachTest { FlagOption("enable-extra-awesomeness", "Enable the extra awesome features.") }

        on("the option not being provided") {
            it("gives the value as false") {
                assertThat(option.value, equalTo(false))
            }
        }

        listOf("--enable-extra-awesomeness", "-a").forEach { format ->
            on("the option being provided in the format $format") {
                val result by runForEachTest { option.parseValue(listOf(format)) }

                it("gives the value as true") {
                    assertThat(option.value, equalTo(true))
                }

                it("returns that one argument was consumed") {
                    assertThat(result, equalTo(OptionParsingResult.ReadOption(1)))
                }
            }

            on("the option being provided with a value in the format $format=something") {
                val result by runForEachTest { option.parseValue(listOf("$format=something")) }

                it("returns that the argument is invalid") {
                    assertThat(result, equalTo(OptionParsingResult.InvalidOption("The option '$format' does not take a value.")))
                }
            }
        }
    }
})
