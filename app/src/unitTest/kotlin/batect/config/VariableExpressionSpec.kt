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

package batect.config

import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.withMessage
import batect.utils.Json
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isEmptyString
import com.natpryce.hamkrest.throws
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object VariableExpressionSpec : Spek({
    describe("a variable expression") {
        describe("parsing from a string") {
            mapOf(
                "some literal value" to LiteralValue("some literal value"),
                "a" to LiteralValue("a"),
                "" to LiteralValue(""),
                "\\\$some literal value" to LiteralValue("\$some literal value"),
                "\\<some literal value" to LiteralValue("<some literal value"),
                "\$SOME_VAR" to EnvironmentVariableReference("SOME_VAR"),
                "\$a" to EnvironmentVariableReference("a"),
                "\$ab" to EnvironmentVariableReference("ab"),
                "\${SOME_VAR}" to EnvironmentVariableReference("SOME_VAR"),
                "\${a}" to EnvironmentVariableReference("a"),
                "\${SOME_VAR:-}" to EnvironmentVariableReference("SOME_VAR", ""),
                "\${SOME_VAR:-default}" to EnvironmentVariableReference("SOME_VAR", "default"),
                "\${SOME_VAR:-some value}" to EnvironmentVariableReference("SOME_VAR", "some value"),
                "\${SOME_VAR:-some value \\} with braces}" to EnvironmentVariableReference("SOME_VAR", "some value } with braces"),
                "\${SOME_VAR:-some value \\a with slashes}" to EnvironmentVariableReference("SOME_VAR", "some value a with slashes"),
                "<SOME_VAR" to ConfigVariableReference("SOME_VAR"),
                "<{SOME_VAR}" to ConfigVariableReference("SOME_VAR"),
                "<{SOME_VAR:}" to ConfigVariableReference("SOME_VAR:"),
                "a\$b" to ConcatenatedExpression(LiteralValue("a"), EnvironmentVariableReference("b")),
                "a\${b}" to ConcatenatedExpression(LiteralValue("a"), EnvironmentVariableReference("b")),
                "a\${b:-c}" to ConcatenatedExpression(LiteralValue("a"), EnvironmentVariableReference("b", "c")),
                "\$a\$b" to ConcatenatedExpression(EnvironmentVariableReference("a"), EnvironmentVariableReference("b")),
                "\$a b" to ConcatenatedExpression(EnvironmentVariableReference("a"), LiteralValue(" b")),
                "\${a}b" to ConcatenatedExpression(EnvironmentVariableReference("a"), LiteralValue("b")),
                "\$abc d" to ConcatenatedExpression(EnvironmentVariableReference("abc"), LiteralValue(" d")),
                "\$a_b c" to ConcatenatedExpression(EnvironmentVariableReference("a_b"), LiteralValue(" c")),
                "\$ab2 c" to ConcatenatedExpression(EnvironmentVariableReference("ab2"), LiteralValue(" c")),
                "\$abc}d" to ConcatenatedExpression(EnvironmentVariableReference("abc"), LiteralValue("}d")),
                "\$abc-d" to ConcatenatedExpression(EnvironmentVariableReference("abc"), LiteralValue("-d")),
                "a<b" to ConcatenatedExpression(LiteralValue("a"), ConfigVariableReference("b")),
                "<a<b" to ConcatenatedExpression(ConfigVariableReference("a"), ConfigVariableReference("b")),
                "<a b" to ConcatenatedExpression(ConfigVariableReference("a"), LiteralValue(" b")),
                "<a\$b" to ConcatenatedExpression(ConfigVariableReference("a"), EnvironmentVariableReference("b")),
                "<{a}\$b" to ConcatenatedExpression(ConfigVariableReference("a"), EnvironmentVariableReference("b")),
                "<a\${b}" to ConcatenatedExpression(ConfigVariableReference("a"), EnvironmentVariableReference("b")),
                "<{a}\${b}" to ConcatenatedExpression(ConfigVariableReference("a"), EnvironmentVariableReference("b")),
                "\$a<b" to ConcatenatedExpression(EnvironmentVariableReference("a"), ConfigVariableReference("b"))
            ).forEach { (source, expectedExpression) ->
                on("parsing the input '$source'") {
                    val expression = VariableExpression.parse(source)

                    it("returns the expected expression") {
                        assertThat(expression, equalTo(expectedExpression))
                    }
                }
            }

            mapOf(
                "\\" to "invalid escape sequence: '\\' at column 1 must be immediately followed by a character to escape",
                "$" to "invalid environment variable reference: '$' at column 1 must be followed by a variable name",
                "\${" to "invalid environment variable reference: '{' at column 2 must be followed by a closing '}'",
                "\${}" to "invalid environment variable reference: '\${}' at column 1 does not contain a variable name",
                "\${some" to "invalid environment variable reference: '{' at column 2 must be followed by a closing '}'",
                "\${some:" to "invalid environment variable reference: ':' at column 7 must be immediately followed by '-'",
                "\${some:}" to "invalid environment variable reference: ':' at column 7 must be immediately followed by '-'",
                "\${some:-b\\" to "invalid environment variable reference: '\\' at column 10 must be immediately followed by a character to escape",
                "\${:-}" to "invalid environment variable reference: '\${:-}' at column 1 does not contain a variable name",
                "\${:-default}" to "invalid environment variable reference: '\${:-default}' at column 1 does not contain a variable name",
                "<" to "invalid config variable reference: '<' at column 1 must be followed by a variable name",
                "<{" to "invalid config variable reference: '{' at column 2 must be followed by a closing '}'",
                "<{some" to "invalid config variable reference: '{' at column 2 must be followed by a closing '}'",
                "<{}" to "invalid config variable reference: '<{}' at column 1 does not contain a variable name"
            ).forEach { (source, error) ->
                on("parsing the input '$source'") {
                    it("throws an appropriate exception") {
                        assertThat({ VariableExpression.parse(source) }, throws<IllegalArgumentException>(withMessage("Invalid expression '$source': $error")))
                    }
                }
            }
        }
    }

    describe("a literal expression") {
        val expression = LiteralValue("abc123")

        on("evaluating the expression") {
            val value = expression.evaluate(emptyMap(), emptyMap())

            it("returns the value") {
                assertThat(value, equalTo("abc123"))
            }
        }

        on("converting the expression to JSON") {
            val json = Json.parser.stringify(VariableExpression.Companion, expression)

            it("returns the value as a string") {
                assertThat(json, equivalentTo("""{"type":"LiteralValue","value":"abc123"}"""))
            }
        }
    }

    describe("an expression that refers to an environment variable") {
        given("the expression has no default value") {
            val expression = EnvironmentVariableReference("THE_VAR")

            given("the referenced environment variable is set") {
                val hostEnvironmentVariables = mapOf("THE_VAR" to "some value")

                on("evaluating the expression") {
                    val value = expression.evaluate(hostEnvironmentVariables, emptyMap())

                    it("returns the value from the host") {
                        assertThat(value, equalTo("some value"))
                    }
                }
            }

            given("the referenced environment variable is not set") {
                val hostEnvironmentVariables = mapOf("SOME_OTHER_VAR" to "some value")

                on("evaluating the expression") {
                    it("throws an appropriate exception") {
                        assertThat({ expression.evaluate(hostEnvironmentVariables, emptyMap()) },
                            throws<VariableExpressionEvaluationException>(withMessage("The host environment variable 'THE_VAR' is not set, and no default value has been provided.")))
                    }
                }
            }

            on("converting the expression to JSON") {
                val json = Json.parser.stringify(VariableExpression.Companion, expression)

                it("returns a string representation of the variable") {
                    assertThat(json, equivalentTo("""{"type":"EnvironmentVariableReference","referenceTo":"THE_VAR"}"""))
                }
            }
        }

        given("the expression has a default value") {
            val expression = EnvironmentVariableReference("THE_VAR", "the default value")

            given("the referenced environment variable is set") {
                val hostEnvironmentVariables = mapOf("THE_VAR" to "some value")

                on("evaluating the expression") {
                    val value = expression.evaluate(hostEnvironmentVariables, emptyMap())

                    it("returns the value from the host") {
                        assertThat(value, equalTo("some value"))
                    }
                }
            }

            given("the referenced environment variable is not set") {
                val hostEnvironmentVariables = mapOf("SOME_OTHER_VAR" to "some value")

                on("evaluating the expression") {
                    val value = expression.evaluate(hostEnvironmentVariables, emptyMap())

                    it("returns the default value") {
                        assertThat(value, equalTo("the default value"))
                    }
                }
            }

            on("converting the expression to JSON") {
                val json = Json.parser.stringify(VariableExpression.Companion, expression)

                it("returns a string representation of the variable and its default value") {
                    assertThat(json, equivalentTo("""{"type":"EnvironmentVariableReference","referenceTo":"THE_VAR","default":"the default value"}"""))
                }
            }
        }
    }

    describe("an expression that refers to a config variable") {
        val expression = ConfigVariableReference("THE_VAR")

        given("the config variable has not been defined") {
            val configVariables = emptyMap<String, String?>()

            on("evaluating the expression") {
                it("throws an appropriate exception") {
                    assertThat({ expression.evaluate(emptyMap(), configVariables) },
                        throws<VariableExpressionEvaluationException>(withMessage("The config variable 'THE_VAR' has not been defined.")))
                }
            }
        }

        given("the config variable has been defined but has not been set and has no default value") {
            val configVariables = mapOf("THE_VAR" to null as String?)

            on("evaluating the expression") {
                it("throws an appropriate exception") {
                    assertThat({ expression.evaluate(emptyMap(), configVariables) },
                        throws<VariableExpressionEvaluationException>(withMessage("The config variable 'THE_VAR' is not set and has no default value.")))
                }
            }
        }

        given("the config variable has a value") {
            val configVariables = mapOf("THE_VAR" to "the value")

            on("evaluating the expression") {
                val value = expression.evaluate(emptyMap(), configVariables)

                it("returns the default value") {
                    assertThat(value, equalTo("the value"))
                }
            }
        }

        on("converting the expression to JSON") {
            val json = Json.parser.stringify(VariableExpression.Companion, expression)

            it("returns a string representation of the variable") {
                assertThat(json, equivalentTo("""{"type":"ConfigVariableReference","referenceTo":"THE_VAR"}"""))
            }
        }
    }

    describe("an expression that concatenates multiple other expressions") {
        given("an empty list of expressions to concatenate") {
            val expression = ConcatenatedExpression()

            on("evaluating the expression") {
                val value = expression.evaluate(emptyMap(), emptyMap())

                it("returns an empty string") {
                    assertThat(value, isEmptyString)
                }
            }

            on("converting the expression to JSON") {
                val json = Json.parser.stringify(VariableExpression.Companion, expression)

                it("returns an empty array") {
                    assertThat(json, equivalentTo("""{"type":"ConcatenatedExpression","expressions":[]}""".trimMargin()))
                }
            }
        }

        given("a single expression to concatenate") {
            val expression = ConcatenatedExpression(LiteralValue("some value"))

            on("evaluating the expression") {
                val value = expression.evaluate(emptyMap(), emptyMap())

                it("returns the result of evaluating that expression") {
                    assertThat(value, equalTo("some value"))
                }
            }
        }

        given("two expressions to concatenate") {
            val expression = ConcatenatedExpression(LiteralValue("some"), LiteralValue(" value"))

            on("evaluating the expression") {
                val value = expression.evaluate(emptyMap(), emptyMap())

                it("returns both expressions concatenated together") {
                    assertThat(value, equalTo("some value"))
                }
            }
        }

        given("three expressions to concatenate") {
            val expression = ConcatenatedExpression(LiteralValue("some"), LiteralValue(" other"), LiteralValue(" value"))

            on("evaluating the expression") {
                val value = expression.evaluate(emptyMap(), emptyMap())

                it("returns all expressions concatenated together") {
                    assertThat(value, equalTo("some other value"))
                }
            }

            on("converting the expression to JSON") {
                val json = Json.parser.stringify(VariableExpression.Companion, expression)

                it("returns an array of expressions") {
                    assertThat(json, equivalentTo("""{
                        |"type":"ConcatenatedExpression",
                        |"expressions":[
                        |   {"type":"LiteralValue","value":"some"},
                        |   {"type":"LiteralValue","value":" other"},
                        |   {"type":"LiteralValue","value":" value"}
                        |]
                        |}""".trimMargin()))
                }
            }
        }
    }
})
