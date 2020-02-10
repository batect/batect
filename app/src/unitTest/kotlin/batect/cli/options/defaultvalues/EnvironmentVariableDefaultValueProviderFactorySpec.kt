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

package batect.cli.options.defaultvalues

import batect.cli.options.ValueConversionResult
import batect.cli.options.ValueConverters
import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object EnvironmentVariableDefaultValueProviderFactorySpec : Spek({
    describe("an environment variable default value provider") {
        val environment = mapOf(
            "SOME_VAR" to "some value"
        )

        val factory = EnvironmentVariableDefaultValueProviderFactory(environment)

        given("the source environment variable is set") {
            given("the value of the environment variable is able to be converted to the target type") {
                val provider = factory.create("SOME_VAR", "the default value", ValueConverters::string)

                it("returns the value of the environment variable") {
                    assertThat(provider.value, equalTo(PossibleValue.Valid("some value")))
                }
            }

            given("the value of the environment variable is able to be converted to the target type") {
                @Suppress("UNUSED_PARAMETER")
                fun converter(value: String): ValueConversionResult<String> {
                    return ValueConversionResult.ConversionFailed("something went wrong")
                }

                val provider = factory.create("SOME_VAR", "the default value", ::converter)

                it("returns the value of the environment variable") {
                    assertThat(provider.value, equalTo(PossibleValue.Invalid("The value of the SOME_VAR environment variable ('some value') is invalid: something went wrong")))
                }
            }
        }

        given("the source environment variable is not set") {
            val provider = factory.create("SOME_OTHER_VAR", "the fallback value", ValueConverters::string)

            it("returns the fallback value") {
                assertThat(provider.value, equalTo(PossibleValue.Valid("the fallback value")))
            }
        }

        describe("getting a description of the default value") {
            given("the source environment variable is set") {
                val sourceVariableName = "SOME_VAR"

                given("the fallback value is not null") {
                    val provider = factory.create(sourceVariableName, "the fallback value", ValueConverters::string)

                    it("returns a description that includes both the current and fallback values") {
                        assertThat(provider.description, equalTo("Defaults to the value of the SOME_VAR environment variable (which is currently 'some value') or 'the fallback value' if SOME_VAR is not set."))
                    }
                }

                given("the fallback value is null") {
                    val provider = factory.create(sourceVariableName, null, ValueConverters::string)

                    it("returns a description that includes only the current value") {
                        assertThat(provider.description, equalTo("Defaults to the value of the SOME_VAR environment variable (which is currently 'some value')."))
                    }
                }
            }

            given("the source environment variable is not set") {
                val sourceVariableName = "SOME_OTHER_VAR"

                given("the fallback value is not null") {
                    val provider = factory.create(sourceVariableName, "the fallback value", ValueConverters::string)

                    it("returns a description that includes both the fallback value and an explanation that the variable is not set") {
                        assertThat(provider.description, equalTo("Defaults to the value of the SOME_OTHER_VAR environment variable (which is currently not set) or 'the fallback value' if SOME_OTHER_VAR is not set."))
                    }
                }

                given("the fallback value is null") {
                    val provider = factory.create(sourceVariableName, null, ValueConverters::string)

                    it("returns a description that includes only an explanation that the variable is not set") {
                        assertThat(provider.description, equalTo("Defaults to the value of the SOME_OTHER_VAR environment variable (which is currently not set)."))
                    }
                }
            }
        }
    }
})
