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

import batect.config.io.deserializers.PathDeserializer
import batect.os.PathResolutionResult
import batect.os.PathType
import batect.testutils.createForEachTest
import batect.testutils.on
import batect.testutils.osIndependentPath
import batect.testutils.runForEachTest
import batect.testutils.withColumn
import batect.testutils.withLineNumber
import batect.testutils.withMessage
import com.charleskorn.kaml.Yaml
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import kotlinx.serialization.Decoder
import kotlinx.serialization.modules.serializersModuleOf
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Paths

object VolumeMountSpec : Spek({
    describe("a volume mount") {
        describe("deserializing from YAML") {
            val pathDeserializer by createForEachTest {
                mock<PathDeserializer> {
                    on { deserialize(any()) } doAnswer { invocation ->
                        val input = invocation.arguments[0] as Decoder
                        val originalPath = input.decodeString()

                        when (originalPath) {
                            "/invalid" -> PathResolutionResult.InvalidPath(originalPath)
                            else -> PathResolutionResult.Resolved(originalPath, osIndependentPath("/resolved" + originalPath), PathType.Other)
                        }
                    }
                }
            }

            val parser by createForEachTest { Yaml(context = serializersModuleOf(PathResolutionResult::class, pathDeserializer)) }

            describe("deserializing from compact form") {
                on("parsing a valid volume mount definition") {
                    val volumeMount by runForEachTest { parser.parse(VolumeMount.Companion, "'/local:/container'") }

                    it("returns the correct local path, resolved to an absolute path") {
                        assertThat(volumeMount.localPath, equalTo("/resolved/local"))
                    }

                    it("returns the correct container path") {
                        assertThat(volumeMount.containerPath, equalTo("/container"))
                    }

                    it("returns the correct options") {
                        assertThat(volumeMount.options, absent())
                    }

                    it("returns the correct string form") {
                        assertThat(volumeMount.toString(), equalTo("/resolved/local:/container"))
                    }
                }

                on("parsing a valid volume mount definition with options") {
                    val volumeMount by runForEachTest { parser.parse(VolumeMount.Companion, "'/local:/container:some_options'") }

                    it("returns the correct local path, resolved to an absolute path") {
                        assertThat(volumeMount.localPath, equalTo("/resolved/local"))
                    }

                    it("returns the correct container path") {
                        assertThat(volumeMount.containerPath, equalTo("/container"))
                    }

                    it("returns the correct options") {
                        assertThat(volumeMount.options, equalTo("some_options"))
                    }

                    it("returns the correct string form") {
                        assertThat(volumeMount.toString(), equalTo("/resolved/local:/container:some_options"))
                    }
                }

                on("parsing an empty volume mount definition") {
                    it("fails with an appropriate error message") {
                        assertThat({ parser.parse(VolumeMount.Companion, "''") }, throws(withMessage("Volume mount definition cannot be empty.") and withLineNumber(1) and withColumn(1)))
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
                    on("parsing the invalid volume mount definition '$it'") {
                        it("fails with an appropriate error message") {
                            assertThat(
                                { parser.parse(VolumeMount.Companion, "'$it'") }, throws(
                                    withMessage("Volume mount definition '$it' is not valid. It must be in the form 'local_path:container_path' or 'local_path:container_path:options'.")
                                        and withLineNumber(1)
                                        and withColumn(1)
                                )
                            )
                        }
                    }
                }

                on("parsing a volume mount definition with an invalid local path") {
                    it("fails with an appropriate error message") {
                        assertThat({ parser.parse(VolumeMount.Companion, "'/invalid:/container'") }, throws(withMessage("'/invalid' is not a valid path.") and withLineNumber(1) and withColumn(1)))
                    }
                }
            }

            describe("deserializing from expanded form") {
                on("parsing a valid volume mount definition") {
                    val yaml = """
                            local: /local
                            container: /container
                        """.trimIndent()

                    val volumeMount by runForEachTest { parser.parse(VolumeMount.Companion, yaml) }

                    it("returns the correct local path, resolved to an absolute path") {
                        assertThat(volumeMount.localPath, equalTo("/resolved/local"))
                    }

                    it("returns the correct container path") {
                        assertThat(volumeMount.containerPath, equalTo("/container"))
                    }

                    it("returns the correct options") {
                        assertThat(volumeMount.options, absent())
                    }

                    it("returns the correct string form") {
                        assertThat(volumeMount.toString(), equalTo("/resolved/local:/container"))
                    }
                }

                on("parsing a valid volume mount definition with options") {
                    val yaml = """
                            local: /local
                            container: /container
                            options: some_options
                        """.trimIndent()

                    val volumeMount by runForEachTest { parser.parse(VolumeMount.Companion, yaml) }

                    it("returns the correct local path, resolved to an absolute path") {
                        assertThat(volumeMount.localPath, equalTo("/resolved/local"))
                    }

                    it("returns the correct container path") {
                        assertThat(volumeMount.containerPath, equalTo("/container"))
                    }

                    it("returns the correct options") {
                        assertThat(volumeMount.options, equalTo("some_options"))
                    }

                    it("returns the correct string form") {
                        assertThat(volumeMount.toString(), equalTo("/resolved/local:/container:some_options"))
                    }
                }

                on("parsing a volume mount definition missing the 'local' field") {
                    val yaml = "container: /container"

                    it("fails with an appropriate error message") {
                        assertThat(
                            { parser.parse(VolumeMount.Companion, yaml) }, throws(
                                withMessage("Field 'local' is required but it is missing.")
                                    and withLineNumber(1)
                                    and withColumn(1)
                            )
                        )
                    }
                }

                on("parsing a volume mount definition missing the 'container' field") {
                    val yaml = "local: /local"

                    it("fails with an appropriate error message") {
                        assertThat(
                            { parser.parse(VolumeMount.Companion, yaml) }, throws(
                                withMessage("Field 'container' is required but it is missing.")
                                    and withLineNumber(1)
                                    and withColumn(1)
                            )
                        )
                    }
                }

                on("parsing a volume mount definition with an invalid local path") {
                    val yaml = """
                        container: /container
                        local: /invalid
                    """.trimIndent()

                    it("fails with an appropriate error message") {
                        assertThat({ parser.parse(VolumeMount.Companion, yaml) }, throws(withMessage("'/invalid' is not a valid path.") and withLineNumber(2) and withColumn(8)))
                    }
                }
            }

            describe("deserializing from something that is neither a string nor a map") {
                val yaml = """
                    - thing
                """.trimIndent()

                it("fails with an appropriate error message") {
                    assertThat(
                        { parser.parse(VolumeMount.Companion, yaml) }, throws(
                            withMessage("Volume mount definition is not valid. It must either be an object or a literal in the form 'local_path:container_path' or 'local_path:container_path:options'.")
                        )
                    )
                }
            }
        }
    }
})
