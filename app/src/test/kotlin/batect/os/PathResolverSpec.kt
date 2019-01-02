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

package batect.os

import batect.testutils.equalTo
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import java.nio.file.Files
import java.util.Properties

object PathResolverSpec : Spek({
    describe("a path resolver") {
        val fileSystem = Jimfs.newFileSystem(Configuration.unix())
        val homeDir = fileSystem.getPath("/home/username")
        val relativeTo = fileSystem.getPath("/thing/place")
        val systemProperties = Properties()
        systemProperties.setProperty("user.home", homeDir.toString())

        val resolver = PathResolver(relativeTo, systemProperties)

        beforeGroup {
            Files.createDirectories(homeDir)
            Files.createDirectories(relativeTo)
        }

        given("an empty path") {
            val path = ""

            it("resolves to the original directory") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.Resolved("", fileSystem.getPath("/thing/place"), PathType.Directory)))
            }
        }

        given("a reference to the current directory") {
            val path = "."

            it("resolves to the original directory") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.Resolved(".", fileSystem.getPath("/thing/place"), PathType.Directory)))
            }
        }

        given("a reference to a subdirectory") {
            val path = "stuff"

            beforeGroup {
                Files.createDirectories(relativeTo.resolve(path))
            }

            it("resolves to the subdirectory") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.Resolved("stuff", fileSystem.getPath("/thing/place/stuff"), PathType.Directory)))
            }
        }

        given("a reference to the parent directory") {
            val path = ".."

            it("resolves to the parent directory") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.Resolved("..", fileSystem.getPath("/thing"), PathType.Directory)))
            }
        }

        given("a reference to a directory in the parent directory") {
            val path = "../something"

            beforeGroup {
                Files.createDirectories(fileSystem.getPath("/thing/something"))
            }

            it("resolves to the parent directory") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.Resolved("../something", fileSystem.getPath("/thing/something"), PathType.Directory)))
            }
        }

        given("a reference an absolute path") {
            val path = "/other"

            beforeGroup {
                Files.createDirectories(fileSystem.getPath(path))
            }

            it("resolves to the absolute path") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.Resolved("/other", fileSystem.getPath("/other"), PathType.Directory)))
            }
        }

        given("a reference to a path that doesn't exist") {
            val path = "doesnotexist"

            it("reports that the path does not exist") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.Resolved("doesnotexist", fileSystem.getPath("/thing/place/doesnotexist"), PathType.DoesNotExist)))
            }
        }

        given("a reference to a file") {
            val path = "thefile.txt"

            beforeGroup {
                Files.createFile(relativeTo.resolve(path))
            }

            it("resolves to the file") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.Resolved("thefile.txt", fileSystem.getPath("/thing/place/thefile.txt"), PathType.File)))
            }
        }

        given("a reference to the home directory") {
            val path = "~"

            it("resolves to the user's home directory") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.Resolved("~", fileSystem.getPath("/home/username"), PathType.Directory)))
            }
        }

        given("a reference to a path within the home directory") {
            val path = "~/somefile.txt"

            beforeGroup {
                Files.createFile(homeDir.resolve("somefile.txt"))
            }

            it("resolves to the full path to the file") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.Resolved("~/somefile.txt", fileSystem.getPath("/home/username/somefile.txt"), PathType.File)))
            }
        }

        given("an invalid path") {
            val path = "\u0000"

            it("reports that the path is invalid") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.InvalidPath("\u0000")))
            }
        }
    }
})
