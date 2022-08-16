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

package batect.os

import batect.testutils.equalTo
import batect.testutils.given
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

object PathResolverSpec : Spek({
    describe("a path resolver") {
        val fileSystemConfiguration = Configuration.unix().toBuilder()
            .setWorkingDirectory("/some-work-dir")
            .build()

        val fileSystem = Jimfs.newFileSystem(fileSystemConfiguration)
        val homeDir = fileSystem.getPath("/home/username")
        val relativeTo = fileSystem.getPath("/thing/place")
        val systemProperties = Properties()
        systemProperties.setProperty("user.home", homeDir.toString())

        val context = object : PathResolutionContext {
            override val relativeTo: Path = relativeTo
            override fun getResolutionDescription(absolutePath: Path): String = "described as $absolutePath"
            override fun getPathForDisplay(absolutePath: Path): String = throw NotImplementedError()
        }

        val resolver = PathResolver(context, systemProperties)

        beforeGroup {
            Files.createDirectories(homeDir)
            Files.createDirectories(relativeTo)
        }

        given("an empty path") {
            val path = ""

            it("resolves to the original directory") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.Resolved("", fileSystem.getPath("/thing/place"), PathType.Directory, "described as /thing/place")))
            }
        }

        given("a reference to the current directory") {
            val path = "."

            it("resolves to the original directory") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.Resolved(".", fileSystem.getPath("/thing/place"), PathType.Directory, "described as /thing/place")))
            }
        }

        given("a reference to a subdirectory") {
            val path = "stuff"

            beforeGroup {
                Files.createDirectories(relativeTo.resolve(path))
            }

            it("resolves to the subdirectory") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.Resolved("stuff", fileSystem.getPath("/thing/place/stuff"), PathType.Directory, "described as /thing/place/stuff")))
            }
        }

        given("a reference to the parent directory") {
            val path = ".."

            it("resolves to the parent directory") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.Resolved("..", fileSystem.getPath("/thing"), PathType.Directory, "described as /thing")))
            }
        }

        given("a reference to a directory in the parent directory") {
            val path = "../something"

            beforeGroup {
                Files.createDirectories(fileSystem.getPath("/thing/something"))
            }

            it("resolves to the parent directory") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.Resolved("../something", fileSystem.getPath("/thing/something"), PathType.Directory, "described as /thing/something")))
            }
        }

        given("a reference an absolute path") {
            val path = "/other"

            beforeGroup {
                Files.createDirectories(fileSystem.getPath(path))
            }

            it("resolves to the absolute path") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.Resolved("/other", fileSystem.getPath("/other"), PathType.Directory, "described as /other")))
            }
        }

        given("a reference to a path that doesn't exist") {
            val path = "doesnotexist"

            it("reports that the path does not exist") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.Resolved("doesnotexist", fileSystem.getPath("/thing/place/doesnotexist"), PathType.DoesNotExist, "described as /thing/place/doesnotexist")))
            }
        }

        given("a reference to a file") {
            val path = "thefile.txt"

            beforeGroup {
                Files.createFile(relativeTo.resolve(path))
            }

            it("resolves to the file") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.Resolved("thefile.txt", fileSystem.getPath("/thing/place/thefile.txt"), PathType.File, "described as /thing/place/thefile.txt")))
            }
        }

        given("a reference to the home directory") {
            val path = "~"

            it("resolves to the user's home directory") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.Resolved("~", fileSystem.getPath("/home/username"), PathType.Directory, "described as /home/username")))
            }
        }

        given("a reference to a path within the home directory") {
            val path = "~/somefile.txt"

            beforeGroup {
                Files.createFile(homeDir.resolve("somefile.txt"))
            }

            it("resolves to the full path to the file") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.Resolved("~/somefile.txt", fileSystem.getPath("/home/username/somefile.txt"), PathType.File, "described as /home/username/somefile.txt")))
            }
        }

        given("an invalid path") {
            val path = "\u0000"

            it("reports that the path is invalid") {
                assertThat(resolver.resolve(path), equalTo(PathResolutionResult.InvalidPath("\u0000")))
            }
        }

        given("a path resolver with the current directory as the base path") {
            val currentDirectoryResolver = PathResolver(DefaultPathResolutionContext(fileSystem.getPath(".")), systemProperties)
            val path = "somefile.txt"

            it("resolves the path to the absolute path") {
                assertThat(
                    currentDirectoryResolver.resolve(path),
                    equalTo(
                        PathResolutionResult.Resolved(
                            "somefile.txt",
                            fileSystem.getPath("/some-work-dir/somefile.txt"),
                            PathType.DoesNotExist,
                            "resolved to '/some-work-dir/somefile.txt'"
                        )
                    )
                )
            }
        }
    }
})
