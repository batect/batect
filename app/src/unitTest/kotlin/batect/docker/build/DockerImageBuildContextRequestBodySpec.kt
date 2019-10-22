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

package batect.docker.build

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.onlyOn
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.hasSize
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.isEmptyString
import jnr.ffi.Platform
import okhttp3.MediaType
import okio.buffer
import okio.sink
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

// Unfortunately we can't use Jimfs here to simulate the filesystems on different operating systems -
// Jimfs does not support Path.toFile(), and we need that in order to create the archive entries.
object DockerImageBuildContextRequestBodySpec : Spek({
    describe("a Docker image build context request body") {
        on("getting the content type") {
            val requestBody = DockerImageBuildContextRequestBody(DockerImageBuildContext(emptySet()))

            it("returns the TAR content type") {
                assertThat(requestBody.contentType(), equalTo(MediaType.get("application/x-tar")))
            }
        }

        on("getting the content length") {
            val requestBody = DockerImageBuildContextRequestBody(DockerImageBuildContext(emptySet()))

            it("returns that the content length is unknown") {
                assertThat(requestBody.contentLength(), equalTo(-1))
            }
        }

        describe("writing the context") {
            val contextDir by createForEachTest { Files.createTempDirectory("${DockerImageBuildContextRequestBodySpec::class.simpleName}-") }

            afterEachTest { contextDir.toFile().deleteRecursively() }

            given("the build context is empty") {
                val context = DockerImageBuildContext(emptySet())
                val requestBody = DockerImageBuildContextRequestBody(context)

                on("writing the request body") {
                    val outputStream = ByteArrayOutputStream()
                    val sink = outputStream.sink().buffer()
                    requestBody.writeTo(sink)

                    val entries = readAllTarEntries(outputStream)

                    it("writes nothing to the output stream") {
                        assertThat(entries, isEmpty)
                    }
                }
            }

            given("the build context contains a single file") {
                val path by createForEachTest { contextDir.resolve("some-file") }

                beforeEachTest {
                    Files.write(path, "Some content in the file\n".toByteArray())
                }

                onlyOn(setOf(Platform.OS.LINUX, Platform.OS.DARWIN)) {
                    beforeEachTest {
                        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw------x"))
                    }
                }

                val context by createForEachTest {
                    DockerImageBuildContext(setOf(
                        DockerImageBuildContextEntry(path, "path-inside-context")
                    ))
                }

                val requestBody by createForEachTest { DockerImageBuildContextRequestBody(context) }

                on("writing the request body") {
                    val outputStream by createForEachTest { ByteArrayOutputStream() }
                    val sink by createForEachTest { outputStream.sink().buffer() }
                    beforeEachTest { requestBody.writeTo(sink) }

                    val entries by runForEachTest { readAllTarEntries(outputStream) }

                    it("writes a single entry to the output stream") {
                        assertThat(entries, hasSize(equalTo(1)))
                    }

                    it("uses the context name for the file") {
                        assertThat(entries[0], has(RequestBodyEntry::name, equalTo("path-inside-context")))
                    }

                    it("includes the content of the file") {
                        assertThat(entries[0], has(RequestBodyEntry::content, equalTo("Some content in the file\n")))
                    }

                    onlyOn(setOf(Platform.OS.LINUX, Platform.OS.DARWIN)) {
                        it("includes the file's original permissions from the filesystem") {
                            assertThat(entries[0], has(RequestBodyEntry::mode, equalTo(Files.getAttribute(path, "unix:mode"))))
                        }
                    }

                    onlyOn(Platform.OS.WINDOWS) {
                        it("sets the file's permissions to the default value for Windows") {
                            assertThat(entries[0], has(RequestBodyEntry::mode, equalTo(0b111_101_101)))
                        }
                    }

                    it("marks the file as a file") {
                        assertThat(entries[0], has(RequestBodyEntry::isFile, equalTo(true)))
                    }

                    it("does not mark the file as a directory") {
                        assertThat(entries[0], has(RequestBodyEntry::isDirectory, equalTo(false)))
                    }
                }
            }

            given("the build context contains a single file with a name over 100 characters long") {
                val path by createForEachTest { contextDir.resolve("some-file") }

                beforeEachTest {
                    Files.createFile(path)
                }

                val nameInsideContext = "0123456789".repeat(10) + "-abc"

                val context by createForEachTest {
                    DockerImageBuildContext(setOf(
                        DockerImageBuildContextEntry(path, nameInsideContext)
                    ))
                }

                val requestBody by createForEachTest { DockerImageBuildContextRequestBody(context) }

                on("writing the request body") {
                    val outputStream by createForEachTest { ByteArrayOutputStream() }
                    val sink by createForEachTest { outputStream.sink().buffer() }
                    beforeEachTest { requestBody.writeTo(sink) }

                    val entries by runForEachTest { readAllTarEntries(outputStream) }

                    it("writes a single entry to the output stream") {
                        assertThat(entries, hasSize(equalTo(1)))
                    }

                    it("uses the context name for the file") {
                        assertThat(entries[0], has(RequestBodyEntry::name, equalTo(nameInsideContext)))
                    }
                }
            }

            given("the build context contains a single directory") {
                val path by createForEachTest { contextDir.resolve("some-directory") }

                beforeEachTest {
                    Files.createDirectory(path)
                }

                onlyOn(setOf(Platform.OS.LINUX, Platform.OS.DARWIN)) {
                    beforeEachTest {
                        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw------x"))
                    }
                }

                val context by createForEachTest {
                    DockerImageBuildContext(setOf(
                        DockerImageBuildContextEntry(path, "path-inside-context")
                    ))
                }

                val requestBody by createForEachTest { DockerImageBuildContextRequestBody(context) }

                on("writing the request body") {
                    val outputStream by createForEachTest { ByteArrayOutputStream() }
                    val sink by createForEachTest { outputStream.sink().buffer() }
                    beforeEachTest { requestBody.writeTo(sink) }

                    val entries by runForEachTest { readAllTarEntries(outputStream) }

                    it("writes a single entry to the output stream") {
                        assertThat(entries, hasSize(equalTo(1)))
                    }

                    it("uses the context name for the directory") {
                        assertThat(entries[0], has(RequestBodyEntry::name, equalTo("path-inside-context/")))
                    }

                    onlyOn(setOf(Platform.OS.LINUX, Platform.OS.DARWIN)) {
                        it("includes the directory's original permissions from the filesystem") {
                            assertThat(entries[0], has(RequestBodyEntry::mode, equalTo(Files.getAttribute(path, "unix:mode"))))
                        }
                    }

                    onlyOn(Platform.OS.WINDOWS) {
                        it("sets the directory's permissions to the default value for Windows") {
                            assertThat(entries[0], has(RequestBodyEntry::mode, equalTo(0b111_101_101)))
                        }
                    }

                    it("does not mark the directory as a file") {
                        assertThat(entries[0], has(RequestBodyEntry::isFile, equalTo(false)))
                    }

                    it("marks the directory as a directory") {
                        assertThat(entries[0], has(RequestBodyEntry::isDirectory, equalTo(true)))
                    }
                }
            }

            given("the build context contains a directory with a file inside that directory") {
                val directoryPath by createForEachTest { contextDir.resolve("some-directory") }
                val filePath by createForEachTest { directoryPath.resolve("some-file") }

                beforeEachTest {
                    Files.createDirectory(directoryPath)
                    Files.write(filePath, listOf("Some content inside the file"))
                }

                val context by createForEachTest {
                    DockerImageBuildContext(setOf(
                        DockerImageBuildContextEntry(directoryPath, "some-dir-context"),
                        DockerImageBuildContextEntry(filePath, "some-dir-context/some-file-context")
                    ))
                }

                val requestBody by createForEachTest { DockerImageBuildContextRequestBody(context) }

                on("writing the request body") {
                    val outputStream by createForEachTest { ByteArrayOutputStream() }
                    val sink by createForEachTest { outputStream.sink().buffer() }
                    beforeEachTest { requestBody.writeTo(sink) }

                    val entries by runForEachTest { readAllTarEntries(outputStream) }

                    it("writes both the directory and file to the output stream") {
                        assertThat(entries.map { it.name }, equalTo(listOf(
                            "some-dir-context/",
                            "some-dir-context/some-file-context"
                        )))
                    }
                }
            }

            given("the build context contains a symlink") {
                val path by createForEachTest { contextDir.resolve("some-file") }
                val targetPath by createForEachTest { contextDir.resolve("the-target-file") }

                beforeEachTest {
                    Files.createFile(targetPath)
                }

                onlyOn(setOf(Platform.OS.LINUX, Platform.OS.DARWIN)) {
                    beforeEachTest {
                        Files.createSymbolicLink(path, targetPath)
                        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw------x"))
                    }
                }

                onlyOn(setOf(Platform.OS.WINDOWS)) {
                    beforeEachTest {
                        createSymbolicLinkOnWindows(path, targetPath)
                    }
                }

                val context by createForEachTest {
                    DockerImageBuildContext(setOf(
                        DockerImageBuildContextEntry(path, "file1")
                    ))
                }

                val requestBody by createForEachTest { DockerImageBuildContextRequestBody(context) }

                on("writing the request body") {
                    val outputStream by createForEachTest { ByteArrayOutputStream() }
                    val sink by createForEachTest { outputStream.sink().buffer() }
                    beforeEachTest { requestBody.writeTo(sink) }

                    val entries by runForEachTest { readAllTarEntries(outputStream) }

                    it("writes a single entry to the output stream") {
                        assertThat(entries, hasSize(equalTo(1)))
                    }

                    it("marks the symlink as a symlink") {
                        assertThat(entries[0].isSymbolicLink, equalTo(true))
                    }

                    it("does not include any content for the symlink") {
                        assertThat(entries[0].content, isEmptyString)
                    }

                    it("links the symlink to the correct file") {
                        assertThat(entries[0].linkTarget, equalTo("the-target-file"))
                    }

                    onlyOn(setOf(Platform.OS.LINUX, Platform.OS.DARWIN)) {
                        it("includes the permissions from the symlink, not the target file") {
                            assertThat(entries[0], has(RequestBodyEntry::mode, equalTo(Files.getAttribute(path, "unix:mode", LinkOption.NOFOLLOW_LINKS))))
                        }
                    }
                }
            }
        }
    }
})

private data class RequestBodyEntry(
    val name: String,
    val mode: Int,
    val isFile: Boolean,
    val isDirectory: Boolean,
    val isSymbolicLink: Boolean,
    val linkTarget: String,
    val content: String
)

private fun readAllTarEntries(outputStream: ByteArrayOutputStream): List<RequestBodyEntry> {
    val inputStream = ByteArrayInputStream(outputStream.toByteArray())
    val reader = TarArchiveInputStream(inputStream)
    val entries = mutableListOf<RequestBodyEntry>()

    while (reader.nextEntry != null) {
        val entry = reader.currentEntry
        val contentStream = ByteArrayOutputStream()
        reader.copyTo(contentStream)

        entries.add(RequestBodyEntry(
            entry.name,
            entry.mode,
            entry.isFile,
            entry.isDirectory,
            entry.isSymbolicLink,
            entry.linkName,
            contentStream.toByteArray().toString(Charset.defaultCharset())
        ))
    }

    return entries
}

// This is required because the Files.createSymbolicLink() method does not
// pass SYMBOLIC_LINK_FLAG_ALLOW_UNPRIVILEGED_CREATE to the Win32 API.
private fun createSymbolicLinkOnWindows(source: Path, target: Path) {
    val process = ProcessBuilder()
        .command("cmd", "/c", "mklink", source.toString(), target.toString())
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .start()

    val exitCode = process.waitFor()
    val output = InputStreamReader(process.inputStream).readText()

    if (exitCode != 0) {
        throw RuntimeException("Creating symbolic link failed: $output")
    }
}
