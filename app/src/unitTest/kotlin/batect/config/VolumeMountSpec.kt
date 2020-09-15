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

import batect.config.includes.GitIncludePathResolutionContext
import batect.config.io.deserializers.PathDeserializer
import batect.os.DefaultPathResolutionContext
import batect.os.PathResolutionContext
import batect.os.PathResolutionResult
import batect.os.PathResolver
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.osIndependentPath
import batect.testutils.runForEachTest
import batect.testutils.withColumn
import batect.testutils.withLineNumber
import batect.testutils.withMessage
import batect.utils.Json
import com.charleskorn.kaml.InvalidPropertyValueException
import com.charleskorn.kaml.Yaml
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import kotlinx.serialization.modules.serializersModuleOf
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object VolumeMountSpec : Spek({
    describe("a volume mount") {
        describe("deserializing from YAML") {
            val pathResolutionContext by createForEachTest { mock<PathResolutionContext>() }

            val pathResolver by createForEachTest {
                mock<PathResolver> {
                    on { context } doReturn pathResolutionContext
                }
            }

            val pathDeserializer by createForEachTest {
                mock<PathDeserializer> {
                    on { this.pathResolver } doReturn pathResolver
                }
            }

            val parser by createForEachTest { Yaml(serializersModule = serializersModuleOf(PathResolutionResult::class, pathDeserializer)) }

            describe("deserializing a local mount from compact form") {
                on("parsing a valid volume mount definition with no options") {
                    val volumeMount by runForEachTest { parser.decodeFromString(VolumeMount.Companion, "'/local:/container'") }

                    it("returns the correct local path") {
                        assertThat((volumeMount as LocalMount).localPath, equalTo(LiteralValue("/local")))
                    }

                    it("returns the correct container path") {
                        assertThat(volumeMount.containerPath, equalTo("/container"))
                    }

                    it("returns the correct options") {
                        assertThat(volumeMount.options, absent())
                    }

                    it("returns the correct path to resolve the local path relative to") {
                        assertThat((volumeMount as LocalMount).pathResolutionContext, equalTo(pathResolutionContext))
                    }
                }

                on("parsing a valid volume mount definition with options") {
                    val volumeMount by runForEachTest { parser.decodeFromString(VolumeMount.Companion, "'/local:/container:some_options'") }

                    it("returns the correct local path") {
                        assertThat((volumeMount as LocalMount).localPath, equalTo(LiteralValue("/local")))
                    }

                    it("returns the correct container path") {
                        assertThat(volumeMount.containerPath, equalTo("/container"))
                    }

                    it("returns the correct options") {
                        assertThat(volumeMount.options, equalTo("some_options"))
                    }

                    it("returns the correct path to resolve the local path relative to") {
                        assertThat((volumeMount as LocalMount).pathResolutionContext, equalTo(pathResolutionContext))
                    }
                }

                mapOf(
                    "c:\\local" to "a lowercase drive letter",
                    "C:\\local" to "an uppercase drive letter"
                ).forEach { (local, description) ->
                    on("parsing a valid Windows volume mount definition with $description") {
                        val volumeMount by runForEachTest { parser.decodeFromString(VolumeMount.Companion, "'$local:/container'") }

                        it("returns the correct local path") {
                            assertThat((volumeMount as LocalMount).localPath, equalTo(LiteralValue(local)))
                        }

                        it("returns the correct container path") {
                            assertThat(volumeMount.containerPath, equalTo("/container"))
                        }

                        it("returns the correct options") {
                            assertThat(volumeMount.options, absent())
                        }

                        it("returns the correct path to resolve the local path relative to") {
                            assertThat((volumeMount as LocalMount).pathResolutionContext, equalTo(pathResolutionContext))
                        }
                    }

                    on("parsing a valid Windows volume mount definition with $description and options") {
                        val volumeMount by runForEachTest { parser.decodeFromString(VolumeMount.Companion, "'$local:/container:cached'") }

                        it("returns the correct local path") {
                            assertThat((volumeMount as LocalMount).localPath, equalTo(LiteralValue(local)))
                        }

                        it("returns the correct container path") {
                            assertThat(volumeMount.containerPath, equalTo("/container"))
                        }

                        it("returns the correct options") {
                            assertThat(volumeMount.options, equalTo("cached"))
                        }

                        it("returns the correct path to resolve the local path relative to") {
                            assertThat((volumeMount as LocalMount).pathResolutionContext, equalTo(pathResolutionContext))
                        }
                    }
                }

                on("parsing an empty volume mount definition") {
                    it("fails with an appropriate error message") {
                        assertThat({ parser.decodeFromString(VolumeMount.Companion, "''") }, throws(withMessage("Volume mount definition cannot be empty.") and withLineNumber(1) and withColumn(1)))
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
                                { parser.decodeFromString(VolumeMount.Companion, "'$it'") },
                                throws(
                                    withMessage("Volume mount definition '$it' is invalid. It must be in the form 'local_path:container_path' or 'local_path:container_path:options'.")
                                        and withLineNumber(1)
                                        and withColumn(1)
                                )
                            )
                        }
                    }
                }
            }

            describe("deserializing a local mount from expanded form") {
                on("parsing a valid volume mount definition") {
                    val yaml = """
                        local: /local
                        container: /container
                    """.trimIndent()

                    val volumeMount by runForEachTest { parser.decodeFromString(VolumeMount.Companion, yaml) }

                    it("returns the correct local path") {
                        assertThat((volumeMount as LocalMount).localPath, equalTo(LiteralValue("/local")))
                    }

                    it("returns the correct container path") {
                        assertThat(volumeMount.containerPath, equalTo("/container"))
                    }

                    it("returns the correct options") {
                        assertThat(volumeMount.options, absent())
                    }

                    it("returns the correct path to resolve the local path relative to") {
                        assertThat((volumeMount as LocalMount).pathResolutionContext, equalTo(pathResolutionContext))
                    }
                }

                on("parsing a valid volume mount definition with options") {
                    val yaml = """
                        local: /local
                        container: /container
                        options: some_options
                    """.trimIndent()

                    val volumeMount by runForEachTest { parser.decodeFromString(VolumeMount.Companion, yaml) }

                    it("returns the correct local path") {
                        assertThat((volumeMount as LocalMount).localPath, equalTo(LiteralValue("/local")))
                    }

                    it("returns the correct container path") {
                        assertThat(volumeMount.containerPath, equalTo("/container"))
                    }

                    it("returns the correct options") {
                        assertThat(volumeMount.options, equalTo("some_options"))
                    }

                    it("returns the correct path to resolve the local path relative to") {
                        assertThat((volumeMount as LocalMount).pathResolutionContext, equalTo(pathResolutionContext))
                    }
                }

                on("parsing a valid volume mount definition with the type explicitly specified") {
                    val yaml = """
                        type: local
                        local: /local
                        container: /container
                    """.trimIndent()

                    val volumeMount by runForEachTest { parser.decodeFromString(VolumeMount.Companion, yaml) }

                    it("returns the correct local path") {
                        assertThat((volumeMount as LocalMount).localPath, equalTo(LiteralValue("/local")))
                    }

                    it("returns the correct container path") {
                        assertThat(volumeMount.containerPath, equalTo("/container"))
                    }

                    it("returns the correct options") {
                        assertThat(volumeMount.options, absent())
                    }

                    it("returns the correct path to resolve the local path relative to") {
                        assertThat((volumeMount as LocalMount).pathResolutionContext, equalTo(pathResolutionContext))
                    }
                }

                on("parsing a volume mount definition missing the 'local' field") {
                    val yaml = "container: /container"

                    it("fails with an appropriate error message") {
                        assertThat(
                            { parser.decodeFromString(VolumeMount.Companion, yaml) },
                            throws(
                                withMessage("Field 'local' is required for local path mounts but it is missing.")
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
                            { parser.decodeFromString(VolumeMount.Companion, yaml) },
                            throws(
                                withMessage("Field 'container' is required but it is missing.")
                                    and withLineNumber(1)
                                    and withColumn(1)
                            )
                        )
                    }
                }

                on("parsing a volume mount definition containing the 'name' field") {
                    val yaml = """
                        local: /local
                        container: /container
                        name: blah
                    """.trimIndent()

                    it("fails with an appropriate error message") {
                        assertThat(
                            { parser.decodeFromString(VolumeMount.Companion, yaml) },
                            throws(
                                withMessage("Field 'name' is not permitted for local path mounts.")
                                    and withLineNumber(1)
                                    and withColumn(1)
                            )
                        )
                    }
                }
            }

            on("deserializing a cache mount from expanded form") {
                on("parsing a valid volume mount definition") {
                    val yaml = """
                        type: cache
                        name: my-cache
                        container: /container
                    """.trimIndent()

                    val volumeMount by runForEachTest { parser.decodeFromString(VolumeMount.Companion, yaml) }

                    it("returns the correct volume name") {
                        assertThat((volumeMount as CacheMount).name, equalTo("my-cache"))
                    }

                    it("returns the correct container path") {
                        assertThat(volumeMount.containerPath, equalTo("/container"))
                    }

                    it("returns the correct options") {
                        assertThat(volumeMount.options, absent())
                    }
                }

                on("parsing a valid volume mount definition with options") {
                    val yaml = """
                        type: cache
                        name: my-cache
                        container: /container
                        options: some_options
                    """.trimIndent()

                    val volumeMount by runForEachTest { parser.decodeFromString(VolumeMount.Companion, yaml) }

                    it("returns the correct volume name") {
                        assertThat((volumeMount as CacheMount).name, equalTo("my-cache"))
                    }

                    it("returns the correct container path") {
                        assertThat(volumeMount.containerPath, equalTo("/container"))
                    }

                    it("returns the correct options") {
                        assertThat(volumeMount.options, equalTo("some_options"))
                    }
                }

                on("parsing a volume mount definition missing the 'name' field") {
                    val yaml = """
                        type: cache
                        container: /container
                    """.trimIndent()

                    it("fails with an appropriate error message") {
                        assertThat(
                            { parser.decodeFromString(VolumeMount.Companion, yaml) },
                            throws(
                                withMessage("Field 'name' is required for cache mounts but it is missing.")
                                    and withLineNumber(1)
                                    and withColumn(1)
                            )
                        )
                    }
                }

                on("parsing a volume mount definition containing the 'local' field") {
                    val yaml = """
                        type: cache
                        name: blah
                        local: /local
                        container: /container
                    """.trimIndent()

                    it("fails with an appropriate error message") {
                        assertThat(
                            { parser.decodeFromString(VolumeMount.Companion, yaml) },
                            throws(
                                withMessage("Field 'local' is not permitted for cache mounts.")
                                    and withLineNumber(1)
                                    and withColumn(1)
                            )
                        )
                    }
                }
            }

            describe("deserializing a mount from expanded form with an unknown type") {
                val yaml = """
                    type: blah
                    container: /container
                """.trimIndent()

                it("fails with an appropriate error message") {
                    assertThat(
                        { parser.decodeFromString(VolumeMount.Companion, yaml) },
                        throws<InvalidPropertyValueException>(
                            withMessage("Value for 'type' is invalid: Value 'blah' is not a valid option, permitted choices are: cache, local")
                                and has(InvalidPropertyValueException::line, equalTo(1))
                                and has(InvalidPropertyValueException::column, equalTo(7))
                        )
                    )
                }
            }

            describe("deserializing from something that is neither a string nor a map") {
                val yaml = """
                    - thing
                """.trimIndent()

                it("fails with an appropriate error message") {
                    assertThat(
                        { parser.decodeFromString(VolumeMount.Companion, yaml) },
                        throws(
                            withMessage("Volume mount definition is invalid. It must either be an object or a literal in the form 'local_path:container_path' or 'local_path:container_path:options'.")
                        )
                    )
                }
            }
        }

        describe("serializing to JSON for logs") {
            describe("serializing a local path mount") {
                given("the default filesystem path resolution context is being used") {
                    val pathResolutionContext by createForEachTest { DefaultPathResolutionContext(osIndependentPath("/root-path")) }
                    val mount by createForEachTest { LocalMount(LiteralValue("/local"), pathResolutionContext, "/container", "ro") }

                    it("serializes to the expected format") {
                        assertThat(
                            Json.forLogging.encodeToString(VolumeMount.serializer(), mount),
                            equivalentTo(
                                """
                                |{
                                |   "type": "local",
                                |   "local": {"type":"LiteralValue","value":"/local"},
                                |   "pathResolutionContext": {
                                |       "type": "default",
                                |       "relativeTo": "/root-path"
                                |   },
                                |   "container": "/container",
                                |   "options": "ro"
                                |}
                                """.trimMargin()
                            )
                        )
                    }
                }

                given("a Git include path resolution context is being used") {
                    val include = GitInclude("https://myrepo.com/bundles/bundle.git", "v1.2.3")
                    val pathResolutionContext by createForEachTest { GitIncludePathResolutionContext(osIndependentPath("/git/repo/includes"), osIndependentPath("/git/repo"), include) }
                    val mount by createForEachTest { LocalMount(LiteralValue("/local"), pathResolutionContext, "/container", "ro") }

                    it("serializes to the expected format") {
                        assertThat(
                            Json.forLogging.encodeToString(VolumeMount.serializer(), mount),
                            equivalentTo(
                                """
                                |{
                                |   "type": "local",
                                |   "local": {"type":"LiteralValue","value":"/local"},
                                |   "pathResolutionContext": {
                                |       "type": "git",
                                |       "relativeTo": "/git/repo/includes",
                                |       "repoRootDirectory": "/git/repo",
                                |       "include": {
                                |           "repo": "https://myrepo.com/bundles/bundle.git",
                                |           "ref": "v1.2.3",
                                |           "path": "batect-bundle.yml"
                                |       }
                                |   },
                                |   "container": "/container",
                                |   "options": "ro"
                                |}
                                """.trimMargin()
                            )
                        )
                    }
                }
            }

            describe("serializing a cache mount") {
                val mount = CacheMount("my-cache", "/container", "ro")

                it("serializes to the expected format") {
                    assertThat(
                        Json.forLogging.encodeToString(VolumeMount.serializer(), mount),
                        equivalentTo(
                            """
                            |{
                            |   "type": "cache",
                            |   "name": "my-cache",
                            |   "container": "/container",
                            |   "options": "ro"
                            |}
                            """.trimMargin()
                        )
                    )
                }
            }
        }
    }
})
