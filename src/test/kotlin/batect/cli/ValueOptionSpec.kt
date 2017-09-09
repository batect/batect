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

package batect.cli

import batect.testutils.CreateForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ValueOptionSpec : Spek({
    describe("a value option") {
        val converter = { value: String ->
            when (value) {
                "valid-value" -> ConversionSucceeded(123)
                else -> ConversionFailed<Int>("'$value' is not a acceptable value.")
            }
        }

        val defaultProvider = mock<DefaultValueProvider<Int>> {
            onGeneric { value } doReturn 9999
        }

        val option by CreateForEachTest(this) {
            ValueOption("option", "Some integer option", defaultProvider, converter)
        }

        on("not applying a value for the option") {
            it("returns the default value") {
                assertThat(option.value, equalTo(9999))
            }
        }

        on("applying a valid value for the option") {
            val result = option.applyValue("valid-value")

            it("does not return an error") {
                assertThat(result, equalTo<ValueApplicationResult>(ValidValue))
            }

            it("returns the converted value as the value") {
                assertThat(option.value, equalTo(123))
            }
        }

        on("applying an invalid value for the option") {
            val result = option.applyValue("invalid-value")

            it("returns an error") {
                assertThat(result, equalTo<ValueApplicationResult>(InvalidValue("'invalid-value' is not a acceptable value.")))
            }
        }

        describe("getting the help description for an option") {
            on("the default value provider not giving any extra information") {
                val noDescriptionDefaultProvider = mock<DefaultValueProvider<Int>> {
                    onGeneric { description } doReturn ""
                }

                val noDefaultDescriptionOption = ValueOption("option", "Some integer option", noDescriptionDefaultProvider, converter)

                it("returns the original description") {
                    assertThat(noDefaultDescriptionOption.descriptionForHelp, equalTo("Some integer option"))
                }
            }

            on("the default value provider giving extra information") {
                val noDescriptionDefaultProvider = mock<DefaultValueProvider<Int>> {
                    onGeneric { description } doReturn "defaults to '1234' if not set"
                }

                val defaultWithDescriptionOption = ValueOption("option", "Some integer option", noDescriptionDefaultProvider, converter)

                it("returns the original description with the additional information from the default value provider") {
                    assertThat(defaultWithDescriptionOption.descriptionForHelp, equalTo("Some integer option (defaults to '1234' if not set)"))
                }
            }
        }
    }
})
