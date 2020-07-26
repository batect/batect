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

import batect.config.io.ConfigurationException
import batect.config.io.deserializers.PathDeserializer
import batect.os.PathResolutionResult
import batect.os.PathResolver
import batect.os.PathType
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.osIndependentPath
import batect.testutils.runForEachTest
import batect.testutils.withColumn
import batect.testutils.withLineNumber
import batect.testutils.withMessage
import batect.utils.Json
import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.serialization.modules.serializersModuleOf
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.dsl.LifecycleAware
import org.spekframework.spek2.style.specification.describe

object IncludeSpec : Spek({
    describe("an include") {
        describe("reading from YAML") {
            val pathResolver by createForEachTest { mock<PathResolver>() }
            val pathDeserializer by createForEachTest { PathDeserializer(pathResolver) }
            val configuration = YamlConfiguration(polymorphismStyle = PolymorphismStyle.Property)
            val parser by createForEachTest { Yaml(configuration = configuration, context = serializersModuleOf(PathResolutionResult::class, pathDeserializer)) }

            fun LifecycleAware.givenPathResolvesTo(type: PathType) {
                val absolutePath = osIndependentPath("/resolved/some/path.yml")

                beforeEachTest {
                    whenever(pathResolver.resolve("../some/path.yml")).doReturn(PathResolutionResult.Resolved("../some/path.yml", absolutePath, type, "described as '/resolved/some/path.yml'"))
                }
            }

            given("a file include") {
                given("it is in simple format") {
                    val yaml = "../some/path.yml"

                    given("the path given resolves to a file") {
                        givenPathResolvesTo(PathType.File)

                        val result by runForEachTest { parser.parse(IncludeConfigSerializer, yaml) }

                        it("returns the resolved path") {
                            assertThat(result, equalTo(FileInclude(osIndependentPath("/resolved/some/path.yml"))))
                        }
                    }

                    given("the path given resolves to a directory") {
                        givenPathResolvesTo(PathType.Directory)

                        it("throws an appropriate exception") {
                            assertThat({ parser.parse(IncludeConfigSerializer, yaml) }, throws<ConfigurationException>(
                                withMessage("'../some/path.yml' (described as '/resolved/some/path.yml') is not a file.") and
                                    withLineNumber(1) and
                                    withColumn(1)
                            ))
                        }
                    }

                    given("the path given does not exist") {
                        givenPathResolvesTo(PathType.DoesNotExist)

                        it("throws an appropriate exception") {
                            assertThat({ parser.parse(IncludeConfigSerializer, yaml) }, throws<ConfigurationException>(
                                withMessage("Included file '../some/path.yml' (described as '/resolved/some/path.yml') does not exist.") and
                                    withLineNumber(1) and
                                    withColumn(1)
                            ))
                        }
                    }
                }

                given("it is in object format") {
                    val yaml = """
                        type: file
                        path: ../some/path.yml
                    """.trimIndent()

                    given("the path given resolves to a file") {
                        givenPathResolvesTo(PathType.File)

                        val result by runForEachTest { parser.parse(IncludeConfigSerializer, yaml) }

                        it("returns the resolved path") {
                            assertThat(result, equalTo(FileInclude(osIndependentPath("/resolved/some/path.yml"))))
                        }
                    }

                    given("the path given resolves to a directory") {
                        givenPathResolvesTo(PathType.Directory)

                        it("throws an appropriate exception") {
                            assertThat({ parser.parse(IncludeConfigSerializer, yaml) }, throws<ConfigurationException>(
                                withMessage("'../some/path.yml' (described as '/resolved/some/path.yml') is not a file.") and
                                    withLineNumber(2) and
                                    withColumn(7)
                            ))
                        }
                    }

                    given("the path given does not exist") {
                        givenPathResolvesTo(PathType.DoesNotExist)

                        it("throws an appropriate exception") {
                            assertThat({ parser.parse(IncludeConfigSerializer, yaml) }, throws<ConfigurationException>(
                                withMessage("Included file '../some/path.yml' (described as '/resolved/some/path.yml') does not exist.") and
                                    withLineNumber(2) and
                                    withColumn(7)
                            ))
                        }
                    }
                }
            }

            given("a Git include") {
                given("it does not have an explicit file name") {
                    val yaml = """
                        type: git
                        repo: https://github.com/my-org/my-repo.git
                        ref: v1.2.3
                    """.trimIndent()

                    val result by runForEachTest { parser.parse(IncludeConfigSerializer, yaml) }

                    it("returns an include with a default file name") {
                        assertThat(result, equalTo(GitInclude("https://github.com/my-org/my-repo.git", "v1.2.3", "batect.yml")))
                    }
                }

                given("it has an explicit file name") {
                    val yaml = """
                        type: git
                        repo: https://github.com/my-org/my-repo.git
                        ref: v1.2.3
                        path: my-batect-bundle.yml
                    """.trimIndent()

                    val result by runForEachTest { parser.parse(IncludeConfigSerializer, yaml) }

                    it("returns an include with that file name") {
                        assertThat(result, equalTo(GitInclude("https://github.com/my-org/my-repo.git", "v1.2.3", "my-batect-bundle.yml")))
                    }
                }
            }
        }

        describe("writing to JSON for logging") {
            given("a file include") {
                val include by createForEachTest { FileInclude(osIndependentPath("/some/path")) }
                val json by createForEachTest { Json.forLogging.stringify(IncludeConfigSerializer, include) }

                it("serializes to the expected JSON") {
                    assertThat(json, equivalentTo("""
                        { "type": "file", "path": "/some/path" }
                    """.trimIndent()))
                }
            }

            given("a Git include") {
                val include by createForEachTest { GitInclude("https://github.com/my-org/my-repo.git", "v1.2.3", "some-file.yml") }
                val json by createForEachTest { Json.forLogging.stringify(IncludeConfigSerializer, include) }

                it("serializes to the expected JSON") {
                    assertThat(json, equivalentTo("""
                        {
                            "type": "git",
                            "repo": "https://github.com/my-org/my-repo.git",
                            "ref": "v1.2.3",
                            "path": "some-file.yml"
                        }
                    """.trimIndent()))
                }
            }
        }
    }
})
