package decompose.config.io

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assert
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
                assert.that(resolver.resolve(path), equalTo(ResolvedToDirectory("/thing/place") as PathResolutionResult))
            }
        }

        given("a reference to the current directory") {
            val path = "."

            it("resolves to the original directory") {
                assert.that(resolver.resolve(path), equalTo(ResolvedToDirectory("/thing/place") as PathResolutionResult))
            }
        }

        given("a reference to a subdirectory") {
            val path = "stuff"
            Files.createDirectories(relativeTo.resolve(path))

            it("resolves to the subdirectory") {
                assert.that(resolver.resolve(path), equalTo(ResolvedToDirectory("/thing/place/stuff") as PathResolutionResult))
            }
        }

        given("a reference to the parent directory") {
            val path = ".."

            it("resolves to the parent directory") {
                assert.that(resolver.resolve(path), equalTo(ResolvedToDirectory("/thing") as PathResolutionResult))
            }
        }

        given("a reference to a directory in the parent directory") {
            val path = "../something"
            Files.createDirectories(fileSystem.getPath("/thing/something"))

            it("resolves to the parent directory") {
                assert.that(resolver.resolve(path), equalTo(ResolvedToDirectory("/thing/something") as PathResolutionResult))
            }
        }

        given("a reference an absolute path") {
            val path = "/other"
            Files.createDirectories(fileSystem.getPath(path))

            it("resolves to the absolute path") {
                assert.that(resolver.resolve(path), equalTo(ResolvedToDirectory("/other") as PathResolutionResult))
            }
        }

        given("a reference to a path that doesn't exist") {
            val path = "doesnotexist"

            it("reports that the path does not exist") {
                assert.that(resolver.resolve(path), equalTo(NotFound("/thing/place/doesnotexist") as PathResolutionResult))
            }
        }

        given("a reference to a file") {
            val path = "thefile.txt"
            Files.createFile(relativeTo.resolve(path))

            it("resolves to the file") {
                assert.that(resolver.resolve(path), equalTo(ResolvedToFile("/thing/place/thefile.txt") as PathResolutionResult))
            }
        }

        given("an invalid path") {
            val path = "\u0000"

            it("reports that the path is invalid") {
                assert.that(resolver.resolve(path), equalTo(InvalidPath as PathResolutionResult))
            }
        }
    }
})
