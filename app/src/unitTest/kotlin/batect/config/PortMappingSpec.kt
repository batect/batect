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

import batect.testutils.on
import batect.testutils.withColumn
import batect.testutils.withLineNumber
import batect.testutils.withMessage
import com.charleskorn.kaml.Yaml
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object PortMappingSpec : Spek({
    describe("a port mapping") {
        describe("deserializing from YAML") {
            describe("deserializing from compact form") {
                on("parsing a valid port mapping definition") {
                    val portMapping = fromYaml("123:456")

                    it("returns the correct local path") {
                        assertThat(portMapping.localPort, equalTo(123))
                    }

                    it("returns the correct container path") {
                        assertThat(portMapping.containerPort, equalTo(456))
                    }
                }

                on("parsing an empty port mapping definition") {
                    it("fails with an appropriate error message") {
                        assertThat({ fromYaml("''") }, throws(withMessage("Port mapping definition cannot be empty.")))
                    }
                }

                listOf(
                    "thing:",
                    "12:",
                    ":thing",
                    ":12",
                    "thing",
                    "12",
                    "thing:12",
                    "12:thing",
                    "-1:12",
                    "12:-1",
                    "0:12",
                    "12:0",
                    " ",
                    ":"
                ).map {
                    on("parsing the invalid port mapping definition '$it'") {
                        it("fails with an appropriate error message") {
                            assertThat({ fromYaml("'$it'") }, throws(withMessage("Port mapping definition '$it' is not valid. It must be in the form 'local_port:container_port' and each port must be a positive integer.")))
                        }
                    }
                }
            }

            describe("deserializing from expanded form") {
                on("parsing a valid port mapping definition") {
                    val portMapping = fromYaml(
                        """
                            local: 123
                            container: 456
                        """.trimIndent()
                    )

                    it("returns the correct local path") {
                        assertThat(portMapping.localPort, equalTo(123))
                    }

                    it("returns the correct container path") {
                        assertThat(portMapping.containerPort, equalTo(456))
                    }
                }

                on("parsing a port mapping with a non-positive local port") {
                    val yaml = """
                            local: 0
                            container: 456
                        """.trimIndent()

                    it("fails with an appropriate error message") {
                        assertThat(
                            { fromYaml(yaml) }, throws(
                                withMessage("Field 'local' is invalid: it must be a positive integer.")
                                    and withLineNumber(1)
                                    and withColumn(8)
                            )
                        )
                    }
                }

                on("parsing a port mapping with a non-positive container port") {
                    val yaml = """
                            local: 123
                            container: 0
                        """.trimIndent()

                    it("fails with an appropriate error message") {
                        assertThat(
                            { fromYaml(yaml) }, throws(
                                withMessage("Field 'container' is invalid: it must be a positive integer.")
                                    and withLineNumber(2)
                                    and withColumn(12)
                            )
                        )
                    }
                }

                on("parsing a port mapping missing the 'local' field") {
                    val yaml = "container: 456"

                    it("fails with an appropriate error message") {
                        assertThat(
                            { fromYaml(yaml) }, throws(
                                withMessage("Field 'local' is required but it is missing.")
                                    and withLineNumber(1)
                                    and withColumn(1)
                            )
                        )
                    }
                }

                on("parsing a port mapping missing the 'container' field") {
                    val yaml = "local: 123"

                    it("fails with an appropriate error message") {
                        assertThat(
                            { fromYaml(yaml) }, throws(
                                withMessage("Field 'container' is required but it is missing.")
                                    and withLineNumber(1)
                                    and withColumn(1)
                            )
                        )
                    }
                }
            }

            describe("deserializing from something that is neither a string nor a map") {
                val yaml = """
                    - thing
                """.trimIndent()

                it("fails with an appropriate error message") {
                    assertThat(
                        { fromYaml(yaml) }, throws(
                            withMessage("Port mapping definition is not valid. It must either be an object or a literal in the form 'local_port:container_port'.")
                        )
                    )
                }
            }
        }
    }
})

private fun fromYaml(yaml: String): PortMapping = Yaml.default.parse(PortMapping.Companion, yaml)
