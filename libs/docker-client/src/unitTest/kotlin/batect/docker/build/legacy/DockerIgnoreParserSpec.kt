/*
   Copyright 2017-2021 Charles Korn.

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

package batect.docker.build.legacy

import batect.docker.DockerException
import batect.docker.build.ImageBuildIgnoreEntry
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files

object DockerIgnoreParserSpec : Spek({
    describe("a .dockerignore file parser") {
        val fileSystem by createForEachTest { Jimfs.newFileSystem(Configuration.unix()) }
        val path by createForEachTest { fileSystem.getPath("/some-dir/.dockerignore") }
        val parser = DockerIgnoreParser()

        beforeEachTest {
            Files.createDirectories(path.parent)
        }

        given("the file does not exist") {
            on("parsing the file") {
                val ignoreList by runForEachTest { parser.parse(path) }

                it("returns an empty list") {
                    assertThat(ignoreList, equalTo(ImageBuildIgnoreList(emptyList())))
                }
            }
        }

        given("the file is empty") {
            beforeEachTest {
                Files.createFile(path)
            }

            on("parsing the file") {
                val ignoreList by runForEachTest { parser.parse(path) }

                it("returns an empty list") {
                    assertThat(ignoreList, equalTo(ImageBuildIgnoreList(emptyList())))
                }
            }
        }

        given("the file contains a single blank line") {
            beforeEachTest {
                Files.write(path, listOf(""))
            }

            on("parsing the file") {
                val ignoreList by runForEachTest { parser.parse(path) }

                it("returns an empty list") {
                    assertThat(ignoreList, equalTo(ImageBuildIgnoreList(emptyList())))
                }
            }
        }

        given("the file contains a single comment") {
            beforeEachTest {
                Files.write(path, listOf("# this is a comment"))
            }

            on("parsing the file") {
                val ignoreList by runForEachTest { parser.parse(path) }

                it("returns an empty list") {
                    assertThat(ignoreList, equalTo(ImageBuildIgnoreList(emptyList())))
                }
            }
        }

        given("the file contains a single exclusion pattern") {
            beforeEachTest {
                Files.write(path, listOf("path/thing.go"))
            }

            on("parsing the file") {
                val ignoreList by runForEachTest { parser.parse(path) }

                it("returns a list with that pattern") {
                    assertThat(
                        ignoreList,
                        equalTo(
                            ImageBuildIgnoreList(
                                listOf(
                                    ImageBuildIgnoreEntry("path/thing.go", false)
                                )
                            )
                        )
                    )
                }
            }
        }

        given("the file contains a single inclusion pattern") {
            beforeEachTest {
                Files.write(path, listOf("!path/thing.go"))
            }

            on("parsing the file") {
                val ignoreList by runForEachTest { parser.parse(path) }

                it("returns a list with that pattern") {
                    assertThat(
                        ignoreList,
                        equalTo(
                            ImageBuildIgnoreList(
                                listOf(
                                    ImageBuildIgnoreEntry("path/thing.go", true)
                                )
                            )
                        )
                    )
                }
            }
        }

        given("the file contains a single exclusion pattern with leading whitespace") {
            beforeEachTest {
                Files.write(path, listOf("   path/thing.go"))
            }

            on("parsing the file") {
                val ignoreList by runForEachTest { parser.parse(path) }

                it("returns a list with that pattern with the whitespace removed") {
                    assertThat(
                        ignoreList,
                        equalTo(
                            ImageBuildIgnoreList(
                                listOf(
                                    ImageBuildIgnoreEntry("path/thing.go", false)
                                )
                            )
                        )
                    )
                }
            }
        }

        given("the file contains a single exclusion pattern with trailing whitespace") {
            beforeEachTest {
                Files.write(path, listOf("path/thing.go   "))
            }

            on("parsing the file") {
                val ignoreList by runForEachTest { parser.parse(path) }

                it("returns a list with that pattern with the whitespace removed") {
                    assertThat(
                        ignoreList,
                        equalTo(
                            ImageBuildIgnoreList(
                                listOf(
                                    ImageBuildIgnoreEntry("path/thing.go", false)
                                )
                            )
                        )
                    )
                }
            }
        }

        given("the file contains a single line with just whitespace") {
            beforeEachTest {
                Files.write(path, listOf("   "))
            }

            on("parsing the file") {
                val ignoreList by runForEachTest { parser.parse(path) }

                it("returns an empty list") {
                    assertThat(ignoreList, equalTo(ImageBuildIgnoreList(emptyList())))
                }
            }
        }

        given("the file contains multiple patterns") {
            beforeEachTest {
                Files.write(
                    path,
                    listOf(
                        "path/thing.go",
                        "something/else",
                        "!not/this"
                    )
                )
            }

            on("parsing the file") {
                val ignoreList by runForEachTest { parser.parse(path) }

                it("returns a list with those patterns") {
                    assertThat(
                        ignoreList,
                        equalTo(
                            ImageBuildIgnoreList(
                                listOf(
                                    ImageBuildIgnoreEntry("path/thing.go", false),
                                    ImageBuildIgnoreEntry("something/else", false),
                                    ImageBuildIgnoreEntry("not/this", true)
                                )
                            )
                        )
                    )
                }
            }
        }

        given("the file contains the pattern '.'") {
            beforeEachTest {
                Files.write(path, listOf("."))
            }

            on("parsing the file") {
                val ignoreList by runForEachTest { parser.parse(path) }

                it("ignores the pattern") {
                    assertThat(ignoreList, equalTo(ImageBuildIgnoreList(emptyList())))
                }
            }
        }

        given("the file contains the pattern '!'") {
            beforeEachTest {
                Files.write(path, listOf("!"))
            }

            on("parsing the file") {
                it("throws an appropriate exception") {
                    assertThat({ parser.parse(path) }, throws<DockerException>(withMessage("The .dockerignore pattern '!' is invalid.")))
                }
            }
        }

        given("the file contains an exclusion pattern with whitespace after the '!'") {
            beforeEachTest {
                Files.write(path, listOf("!   path/thing.go"))
            }

            on("parsing the file") {
                val ignoreList by runForEachTest { parser.parse(path) }

                it("returns a list with that pattern with the whitespace removed") {
                    assertThat(
                        ignoreList,
                        equalTo(
                            ImageBuildIgnoreList(
                                listOf(
                                    ImageBuildIgnoreEntry("path/thing.go", true)
                                )
                            )
                        )
                    )
                }
            }
        }

        given("the file contains patterns with leading slashes") {
            beforeEachTest {
                Files.write(
                    path,
                    listOf(
                        "/path/thing.go",
                        "!/path/otherthing.go"
                    )
                )
            }

            on("parsing the file") {
                val ignoreList by runForEachTest { parser.parse(path) }

                it("returns a list with those patterns with the slashes removed") {
                    assertThat(
                        ignoreList,
                        equalTo(
                            ImageBuildIgnoreList(
                                listOf(
                                    ImageBuildIgnoreEntry("path/thing.go", false),
                                    ImageBuildIgnoreEntry("path/otherthing.go", true)
                                )
                            )
                        )
                    )
                }
            }
        }

        // The following tests are based on https://golang.org/src/path/filepath/path_test.go, which is what the Docker CLI uses.
        given("the file contains patterns with multiple consecutive slashes") {
            beforeEachTest {
                Files.write(
                    path,
                    listOf(
                        "path//other///thing.go",
                        "!path//otherthing.go"
                    )
                )
            }

            on("parsing the file") {
                val ignoreList by runForEachTest { parser.parse(path) }

                it("returns a list with those patterns with the extra slashes removed") {
                    assertThat(
                        ignoreList,
                        equalTo(
                            ImageBuildIgnoreList(
                                listOf(
                                    ImageBuildIgnoreEntry("path/other/thing.go", false),
                                    ImageBuildIgnoreEntry("path/otherthing.go", true)
                                )
                            )
                        )
                    )
                }
            }
        }

        given("the file contains patterns with references to parent directories") {
            mapOf(
                ".." to "..",
                "../.." to "../..",
                "../../abc" to "../../abc",
                "../" to "..",
                "../../" to "../..",
                "abc/def/ghi/../jkl" to "abc/def/jkl",
                "abc/def/../ghi/../jkl" to "abc/jkl",
                "abc/def/.." to "abc",
                "abc/def/../.." to ".",
                "/abc/def/../.." to ".",
                "abc/def/../../.." to "..",
                "/abc/def/../../.." to ".",
                "abc/def/../../../ghi/jkl/../../../mno" to "../../mno",
                "/../abc" to "abc",
                "my.file" to "my.file",
                "my..file" to "my..file",
                "my\\/file" to "my\\/file",
                "my\\/.\\/file" to "my\\/.\\/file"
            ).forEach { (patternInFile, expectedCleanedPattern) ->
                given("the pattern is '$patternInFile'") {
                    beforeEachTest {
                        Files.write(path, listOf(patternInFile))
                    }

                    on("parsing the file") {
                        val ignoreList by runForEachTest { parser.parse(path) }

                        it("returns a list with the pattern simplified where possible") {
                            assertThat(
                                ignoreList,
                                equalTo(
                                    ImageBuildIgnoreList(
                                        listOf(
                                            ImageBuildIgnoreEntry(expectedCleanedPattern, false)
                                        )
                                    )
                                )
                            )
                        }
                    }
                }
            }
        }

        given("the file contains patterns with references to the current directory") {
            mapOf(
                "abc/./def" to "abc/def",
                "/./abc/def" to "abc/def",
                "abc/." to "abc"
            ).forEach { (patternInFile, expectedCleanedPattern) ->
                given("the pattern is '$patternInFile'") {
                    beforeEachTest {
                        Files.write(path, listOf(patternInFile))
                    }

                    on("parsing the file") {
                        val ignoreList by runForEachTest { parser.parse(path) }

                        it("returns a list with the pattern simplified where possible") {
                            assertThat(
                                ignoreList,
                                equalTo(
                                    ImageBuildIgnoreList(
                                        listOf(
                                            ImageBuildIgnoreEntry(expectedCleanedPattern, false)
                                        )
                                    )
                                )
                            )
                        }
                    }
                }
            }
        }

        given("the file contains patterns with references to both the current directory and parent directories") {
            mapOf(
                "abc/./../def" to "def",
                "abc//./../def" to "def",
                "abc/../../././../def" to "../../def"
            ).forEach { (patternInFile, expectedCleanedPattern) ->
                given("the pattern is '$patternInFile'") {
                    beforeEachTest {
                        Files.write(path, listOf(patternInFile))
                    }

                    on("parsing the file") {
                        val ignoreList by runForEachTest { parser.parse(path) }

                        it("returns a list with the pattern simplified where possible") {
                            assertThat(
                                ignoreList,
                                equalTo(
                                    ImageBuildIgnoreList(
                                        listOf(
                                            ImageBuildIgnoreEntry(expectedCleanedPattern, false)
                                        )
                                    )
                                )
                            )
                        }
                    }
                }
            }
        }
    }
})
