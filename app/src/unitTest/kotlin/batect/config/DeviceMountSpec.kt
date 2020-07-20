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

import batect.testutils.createForEachTest
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withColumn
import batect.testutils.withLineNumber
import batect.testutils.withMessage
import com.charleskorn.kaml.MissingRequiredPropertyException
import com.charleskorn.kaml.Yaml
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DeviceMountSpec : Spek({
    describe("a device mount") {
        describe("deserializing from YAML") {

            val parser by createForEachTest { Yaml() }

            describe("deserializing from compact form") {
                on("parsing a valid device mount definition without options") {
                    val deviceMount by runForEachTest { parser.parse(DeviceMountConfigSerializer, "'/local:/container'") }

                    it("returns the correct local path") {
                        assertThat(deviceMount.localPath, equalTo("/local"))
                    }

                    it("returns the correct container path") {
                        assertThat(deviceMount.containerPath, equalTo("/container"))
                    }

                    it("returns the correct options") {
                        assertThat(deviceMount.options, absent())
                    }

                    it("returns the correct string form") {
                        assertThat(deviceMount.toString(), equalTo("/local:/container"))
                    }
                }

                on("parsing a valid device mount definition with options") {
                    val deviceMount by runForEachTest { parser.parse(DeviceMountConfigSerializer, "'/local:/container:some_options'") }

                    it("returns the correct local path") {
                        assertThat(deviceMount.localPath, equalTo("/local"))
                    }

                    it("returns the correct container path") {
                        assertThat(deviceMount.containerPath, equalTo("/container"))
                    }

                    it("returns the correct options") {
                        assertThat(deviceMount.options, equalTo("some_options"))
                    }

                    it("returns the correct string form") {
                        assertThat(deviceMount.toString(), equalTo("/local:/container:some_options"))
                    }
                }

                on("parsing an empty device mount definition") {
                    it("fails with an appropriate error message") {
                        assertThat({ parser.parse(DeviceMountConfigSerializer, "''") }, throws(withMessage("Device mount definition cannot be empty.") and withLineNumber(1) and withColumn(1)))
                    }
                }

                listOf(
                    "thing:",
                    ":thing",
                    "thing",
                    " ",
                    ":",
                    "thing:thing:",
                    "thing:thing:options:"
                ).map {
                    on("parsing the invalid device mount definition '$it'") {
                        it("fails with an appropriate error message") {
                            assertThat(
                                { parser.parse(DeviceMountConfigSerializer, "'$it'") }, throws(
                                    withMessage("Device mount definition '$it' is invalid. It must be in the form 'local_path:container_path' or 'local_path:container_path:options'.")
                                        and withLineNumber(1)
                                        and withColumn(1)
                                )
                            )
                        }
                    }
                }
            }

            describe("deserializing from expanded form") {
                on("parsing a valid device mount definition") {
                    val yaml = """
                            local: /local
                            container: /container
                        """.trimIndent()

                    val deviceMount by runForEachTest { parser.parse(DeviceMountConfigSerializer, yaml) }

                    it("returns the correct local path") {
                        assertThat(deviceMount.localPath, equalTo("/local"))
                    }

                    it("returns the correct container path") {
                        assertThat(deviceMount.containerPath, equalTo("/container"))
                    }

                    it("returns the correct options") {
                        assertThat(deviceMount.options, absent())
                    }

                    it("returns the correct string form") {
                        assertThat(deviceMount.toString(), equalTo("/local:/container"))
                    }
                }

                on("parsing a valid device mount definition with options") {
                    val yaml = """
                            local: /local
                            container: /container
                            options: some_options
                        """.trimIndent()

                    val deviceMount by runForEachTest { parser.parse(DeviceMountConfigSerializer, yaml) }

                    it("returns the correct local path") {
                        assertThat(deviceMount.localPath, equalTo("/local"))
                    }

                    it("returns the correct container path") {
                        assertThat(deviceMount.containerPath, equalTo("/container"))
                    }

                    it("returns the correct options") {
                        assertThat(deviceMount.options, equalTo("some_options"))
                    }

                    it("returns the correct string form") {
                        assertThat(deviceMount.toString(), equalTo("/local:/container:some_options"))
                    }
                }

                on("parsing a device mount definition missing the 'local' field") {
                    val yaml = "container: /container"

                    it("fails with an appropriate error message") {
                        assertThat({ parser.parse(DeviceMountConfigSerializer, yaml) }, throws<MissingRequiredPropertyException>(withPropertyName("local")))
                    }
                }

                on("parsing a device mount definition missing the 'container' field") {
                    val yaml = "local: /local"

                    it("fails with an appropriate error message") {
                        assertThat({ parser.parse(DeviceMountConfigSerializer, yaml) }, throws<MissingRequiredPropertyException>(withPropertyName("container")))
                    }
                }
            }

            describe("deserializing from something that is neither a string nor a map") {
                val yaml = """
                    - thing
                """.trimIndent()

                it("fails with an appropriate error message") {
                    assertThat(
                        { parser.parse(DeviceMountConfigSerializer, yaml) }, throws(
                            withMessage("Device mount definition is invalid. It must either be an object or a literal in the form 'local_path:container_path' or 'local_path:container_path:options'.")
                        )
                    )
                }
            }
        }
    }
})

fun withPropertyName(propertyName: String): Matcher<MissingRequiredPropertyException> = has(MissingRequiredPropertyException::propertyName, equalTo(propertyName))
