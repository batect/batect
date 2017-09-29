/*
   Copyright 2017 Charles Korn.

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

package batect.config.io

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import java.nio.file.Files

object PathResolverSpec : Spek({
    describe("a path resolver") {
        val fileSystem = Jimfs.newFileSystem(Configuration.unix())
        val relativeTo = fileSystem.getPath("/thing/place")
        val resolver = PathResolver(relativeTo)

        beforeGroup {
            Files.createDirectories(relativeTo)
        }

        given("an empty path") {
            val path = ""

            it("resolves to the original directory") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.ResolvedToDirectory("/thing/place") as PathResolutionResult))
            }
        }

        given("a reference to the current directory") {
            val path = "."

            it("resolves to the original directory") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.ResolvedToDirectory("/thing/place") as PathResolutionResult))
            }
        }

        given("a reference to a subdirectory") {
            val path = "stuff"
            Files.createDirectories(relativeTo.resolve(path))

            it("resolves to the subdirectory") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.ResolvedToDirectory("/thing/place/stuff") as PathResolutionResult))
            }
        }

        given("a reference to the parent directory") {
            val path = ".."

            it("resolves to the parent directory") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.ResolvedToDirectory("/thing") as PathResolutionResult))
            }
        }

        given("a reference to a directory in the parent directory") {
            val path = "../something"
            Files.createDirectories(fileSystem.getPath("/thing/something"))

            it("resolves to the parent directory") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.ResolvedToDirectory("/thing/something") as PathResolutionResult))
            }
        }

        given("a reference an absolute path") {
            val path = "/other"
            Files.createDirectories(fileSystem.getPath(path))

            it("resolves to the absolute path") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.ResolvedToDirectory("/other") as PathResolutionResult))
            }
        }

        given("a reference to a path that doesn't exist") {
            val path = "doesnotexist"

            it("reports that the path does not exist") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.NotFound("/thing/place/doesnotexist") as PathResolutionResult))
            }
        }

        given("a reference to a file") {
            val path = "thefile.txt"
            Files.createFile(relativeTo.resolve(path))

            it("resolves to the file") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.ResolvedToFile("/thing/place/thefile.txt") as PathResolutionResult))
            }
        }

        given("an invalid path") {
            val path = "\u0000"

            it("reports that the path is invalid") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.InvalidPath as PathResolutionResult))
            }
        }
    }
})
