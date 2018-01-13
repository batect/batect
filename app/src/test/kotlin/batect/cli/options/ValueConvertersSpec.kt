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

package batect.cli.options

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it

object ValueConvertersSpec : Spek({
    describe("value converters") {
        describe("string value converter") {
            it("returns the value passed to it") {
                assertThat(ValueConverters.string("some-value"),
                        equalTo<ValueConversionResult<String>>(ValueConversionResult.ConversionSucceeded("some-value")))
            }
        }

        describe("positive integer value converter") {
            given("a positive integer") {
                it("returns the parsed representation of that integer") {
                    assertThat(ValueConverters.positiveInteger("1"),
                            equalTo<ValueConversionResult<Int>>(ValueConversionResult.ConversionSucceeded(1)))
                }
            }

            given("zero") {
                it("returns an error") {
                    assertThat(ValueConverters.positiveInteger("0"),
                            equalTo<ValueConversionResult<Int>>(ValueConversionResult.ConversionFailed("Value must be positive.")))
                }
            }

            given("a negative integer") {
                it("returns an error") {
                    assertThat(ValueConverters.positiveInteger("-1"),
                            equalTo<ValueConversionResult<Int>>(ValueConversionResult.ConversionFailed("Value must be positive.")))
                }
            }

            given("an empty string") {
                it("returns an error") {
                    assertThat(ValueConverters.positiveInteger(""),
                            equalTo<ValueConversionResult<Int>>(ValueConversionResult.ConversionFailed("Value is not a valid integer.")))
                }
            }

            given("a hexadecimal number") {
                it("returns an error") {
                    assertThat(ValueConverters.positiveInteger("123AAA"),
                            equalTo<ValueConversionResult<Int>>(ValueConversionResult.ConversionFailed("Value is not a valid integer.")))
                }
            }

            given("something that is not a number") {
                it("returns an error") {
                    assertThat(ValueConverters.positiveInteger("x"),
                            equalTo<ValueConversionResult<Int>>(ValueConversionResult.ConversionFailed("Value is not a valid integer.")))
                }
            }
        }
    }
})
