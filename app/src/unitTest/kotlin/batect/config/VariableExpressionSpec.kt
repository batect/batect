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

package batect.config

import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object VariableExpressionSpec : Spek({
    describe("a variable expression") {
        describe("parsing from a string") {
            mapOf(
                "some literal value" to LiteralValue("some literal value"),
                "a" to LiteralValue("a"),
                "\\\$some literal value" to LiteralValue("\$some literal value"),
                "\$SOME_VAR" to ReferenceValue("SOME_VAR"),
                "\$a" to ReferenceValue("a"),
                "\$ab" to ReferenceValue("ab"),
                "\${SOME_VAR}" to ReferenceValue("SOME_VAR"),
                "\${a}" to ReferenceValue("a"),
                "\${SOME_VAR:-}" to ReferenceValue("SOME_VAR", ""),
                "\${SOME_VAR:-default}" to ReferenceValue("SOME_VAR", "default"),
                "\${SOME_VAR:-some value}" to ReferenceValue("SOME_VAR", "some value")
            ).forEach { (source, expectedExpression) ->
                on("parsing the input '$source'") {
                    val expression = VariableExpression.parse(source)

                    it("returns the expected expression") {
                        assertThat(expression, equalTo(expectedExpression))
                    }
                }
            }

            listOf(
                "$",
                "\${",
                "\${}",
                "\${some",
                "\${some:",
                "\${some:}",
                "\${:-}",
                "\${:-default}"
            ).forEach { source ->
                on("parsing the input '$source'") {
                    it("throws an appropriate exception") {
                        assertThat({ VariableExpression.parse(source) }, throws<IllegalArgumentException>(withMessage("Invalid expression '$source'")))
                    }
                }
            }
        }
    }

    describe("a literal expression") {
        val expression = LiteralValue("abc123")

        on("evaluating the expression") {
            val value = expression.evaluate(emptyMap())

            it("returns the value") {
                assertThat(value, equalTo("abc123"))
            }
        }
    }

    describe("an expression that refers to another environment variable") {
        given("the expression has no default value") {
            val expression = ReferenceValue("THE_VAR")

            given("the referenced environment variable is set") {
                val hostEnvironmentVariables = mapOf("THE_VAR" to "some value")

                on("evaluating the expression") {
                    val value = expression.evaluate(hostEnvironmentVariables)

                    it("returns the value from the host") {
                        assertThat(value, equalTo("some value"))
                    }
                }
            }

            given("the referenced environment variable is not set") {
                val hostEnvironmentVariables = mapOf("SOME_OTHER_VAR" to "some value")

                on("evaluating the expression") {
                    it("throws an appropriate exception") {
                        assertThat({ expression.evaluate(hostEnvironmentVariables) },
                            throws<EnvironmentVariableExpressionEvaluationException>(withMessage("The host environment variable 'THE_VAR' is not set, and no default value has been provided.")))
                    }
                }
            }
        }

        given("the expression has a default value") {
            val expression = ReferenceValue("THE_VAR", "the default value")

            given("the referenced environment variable is set") {
                val hostEnvironmentVariables = mapOf("THE_VAR" to "some value")

                on("evaluating the expression") {
                    val value = expression.evaluate(hostEnvironmentVariables)

                    it("returns the value from the host") {
                        assertThat(value, equalTo("some value"))
                    }
                }
            }

            given("the referenced environment variable is not set") {
                val hostEnvironmentVariables = mapOf("SOME_OTHER_VAR" to "some value")

                on("evaluating the expression") {
                    val value = expression.evaluate(hostEnvironmentVariables)

                    it("returns the default value") {
                        assertThat(value, equalTo("the default value"))
                    }
                }
            }
        }
    }
})
