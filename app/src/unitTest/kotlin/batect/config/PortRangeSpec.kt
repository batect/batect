/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.config

import batect.config.io.ConfigurationException
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.withColumn
import batect.testutils.withLineNumber
import batect.testutils.withMessage
import batect.testutils.withPath
import batect.utils.Json
import com.charleskorn.kaml.Yaml
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object PortRangeSpec : Spek({
    describe("a port range") {
        describe("creating a port range") {
            on("creating a port range with a single port") {
                val range = PortRange(123)

                it("reports a size of 1") {
                    assertThat(range.size, equalTo(1))
                }
            }

            on("creating a port range with a single port specified with as an interval") {
                val range = PortRange(123, 123)

                it("reports a size of 1") {
                    assertThat(range.size, equalTo(1))
                }
            }

            on("creating a port range with multiple ports port") {
                val range = PortRange(123, 125)

                it("reports a size of 3") {
                    assertThat(range.size, equalTo(3))
                }
            }

            on("creating a port range with the from and to port inverted") {
                it("throws an appropriate exception") {
                    assertThat({ PortRange(100, 50) }, throws<InvalidPortRangeException>(withMessage("Port range limits must be given in ascending order.")))
                }
            }

            on("creating a port range with the from port set to 0") {
                it("throws an appropriate exception") {
                    assertThat({ PortRange(0, 50) }, throws<InvalidPortRangeException>(withMessage("Ports must be positive integers.")))
                }
            }

            on("creating a port range with the from port set to a negative value") {
                it("throws an appropriate exception") {
                    assertThat({ PortRange(-1, 50) }, throws<InvalidPortRangeException>(withMessage("Ports must be positive integers.")))
                }
            }
        }

        describe("parsing and deserializing a port range") {
            mapOf(
                "123" to PortRange(123),
                "123-123" to PortRange(123),
                "123-456" to PortRange(123, 456),
            ).forEach { (input, expectedValue) ->
                given("the input string '$input'") {
                    it("parses to the expected value") {
                        assertThat(PortRange.parse(input), equalTo(expectedValue))
                    }

                    it("deserializes to the expected value") {
                        assertThat(Yaml.default.decodeFromString(PortRange.serializer(), input), equalTo(expectedValue))
                    }
                }
            }

            setOf(
                "1-a",
                "a-1",
                "1-",
                "-1",
                "a",
                "-1-10",
            ).forEach { input ->
                given("the input string '$input'") {
                    it("throws an appropriate exception when parsed") {
                        assertThat({ PortRange.parse(input) }, throws<InvalidPortRangeException>(withMessage("Port range '$input' is invalid. It must be in the form 'port' or 'from-to' and each port must be a positive integer.")))
                    }

                    it("deserializes to the expected value") {
                        assertThat(
                            {
                                Yaml.default.decodeFromString(PortRange.serializer(), input)
                            },
                            throws<ConfigurationException>(
                                withMessage("Port range '$input' is invalid. It must be in the form 'port' or 'from-to' and each port must be a positive integer.") and withLineNumber(1) and withColumn(1) and withPath("<root>"),
                            ),
                        )
                    }
                }
            }

            given("an empty input string") {
                it("throws an appropriate exception when parsed") {
                    assertThat({ PortRange.parse("") }, throws<InvalidPortRangeException>(withMessage("Port range '' is invalid. It must be in the form 'port' or 'from-to' and each port must be a positive integer.")))
                }

                it("deserializes to the expected value") {
                    assertThat({
                        Yaml.default.decodeFromString(PortRange.serializer(), "''")
                    }, throws<ConfigurationException>(withMessage("Port range '' is invalid. It must be in the form 'port' or 'from-to' and each port must be a positive integer.") and withLineNumber(1) and withColumn(1) and withPath("<root>")))
                }
            }

            setOf(
                "0-10",
                "0",
            ).forEach { input ->
                given("the input string '$input' representing an invalid port range") {
                    it("throws an appropriate exception when parsed") {
                        assertThat({ PortRange.parse(input) }, throws<InvalidPortRangeException>(withMessage("Port range '$input' is invalid. Ports must be positive integers.")))
                    }

                    it("deserializes to the expected value") {
                        assertThat({
                            Yaml.default.decodeFromString(PortRange.serializer(), input)
                        }, throws<ConfigurationException>(withMessage("Port range '$input' is invalid. Ports must be positive integers.") and withLineNumber(1) and withColumn(1) and withPath("<root>")))
                    }
                }
            }

            given("a string with the from and to port inverted") {
                val input = "123-122"

                it("throws an appropriate exception when parsed") {
                    assertThat({ PortRange.parse(input) }, throws<InvalidPortRangeException>(withMessage("Port range '$input' is invalid. Port range limits must be given in ascending order.")))
                }

                it("deserializes to the expected value") {
                    assertThat({
                        Yaml.default.decodeFromString(PortRange.serializer(), input)
                    }, throws<ConfigurationException>(withMessage("Port range '$input' is invalid. Port range limits must be given in ascending order.") and withLineNumber(1) and withColumn(1) and withPath("<root>")))
                }
            }
        }

        describe("serializing and formatting a port range for display") {
            given("a port range with a single port") {
                val range = PortRange(123)

                it("converts to the expected string") {
                    assertThat(range.toString(), equalTo("123"))
                }

                it("serializes to the expected JSON value") {
                    assertThat(Json.forLogging.encodeToString(PortRange.serializer(), range), equalTo(""""123""""))
                }
            }

            given("a port range with multiple ports") {
                val range = PortRange(123, 456)

                it("converts to the expected string") {
                    assertThat(range.toString(), equalTo("123-456"))
                }

                it("serializes to the expected JSON value") {
                    assertThat(Json.forLogging.encodeToString(PortRange.serializer(), range), equalTo(""""123-456""""))
                }
            }
        }
    }
})
