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

package batect.config

import batect.testutils.equalTo
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object EnvironmentVariableExpressionSpec : Spek({
    describe("an environment variable expression") {
        describe("parsing it from a string") {
            given("the string is a literal value") {
                val source = "some literal value"

                on("parsing that string") {
                    val expression = EnvironmentVariableExpression.parse(source)

                    it("returns a literal value expression") {
                        assertThat(expression, equalTo(LiteralValue("some literal value")))
                    }
                }
            }

            given("the string is a reference to another environment variable") {
                val source = "\$SOME_VAR"

                on("parsing that string") {
                    val expression = EnvironmentVariableExpression.parse(source)

                    it("returns a reference expression") {
                        assertThat(expression, equalTo(ReferenceValue("SOME_VAR")))
                    }
                }
            }
        }

        describe("parsing it from an integer") {
            val source = 12

            on("parsing the value") {
                val expression = EnvironmentVariableExpression.parse(source)

                it("returns a literal value expression") {
                    assertThat(expression, equalTo(LiteralValue("12")))
                }
            }
        }

        describe("parsing it from a long") {
            val source = 12L

            on("parsing the value") {
                val expression = EnvironmentVariableExpression.parse(source)

                it("returns a literal value expression") {
                    assertThat(expression, equalTo(LiteralValue("12")))
                }
            }
        }
    }

    describe("a literal environment variable expression") {
        val expression = LiteralValue("abc123")

        on("evaluating the expression") {
            val value = expression.evaluate(emptyMap())

            it("returns the value") {
                assertThat(value, equalTo("abc123"))
            }
        }
    }

    describe("an environment variable expression that refers to another environment variable") {
        val expression = ReferenceValue("THE_VAR")

        given("the referenced environment variable is set") {
            val hostEnvironmentVariables = mapOf("THE_VAR" to "some value")

            on("evaluating the expression") {
                val value = expression.evaluate(hostEnvironmentVariables)

                it("returns the value") {
                    assertThat(value, equalTo("some value"))
                }
            }
        }

        given("the referenced environment variable is not set") {
            val hostEnvironmentVariables = mapOf("SOME_OTHER_VAR" to "some value")

            on("evaluating the expression") {
                it("throws an appropriate exception") {
                    assertThat({ expression.evaluate(hostEnvironmentVariables) },
                        throws<EnvironmentVariableExpressionEvaluationException>(withMessage("The host environment variable 'THE_VAR' is not set.")))
                }
            }
        }
    }
})
