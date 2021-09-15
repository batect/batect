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

package batect.docker.api

import batect.docker.ContainerDirectory
import batect.docker.ContainerFile
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.anyElement
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.hasSize
import okhttp3.MediaType.Companion.toMediaType
import okio.buffer
import okio.sink
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarFile
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayOutputStream

object FilesystemUploadRequestBodySpec : Spek({
    describe("a filesystem upload request body") {
        given("a set of files and directories to upload") {
            val fileContent = "file contents"
            val fileBytes = fileContent.toByteArray(Charsets.UTF_8)

            val itemsToUpload = setOf(
                // Using a user id larger than 2097151 because this will trigger
                //   `java.lang.IllegalArgumentException: user id '300000000' is too big ( > 2097151 ).`
                //   if the underlying TarArchiveOutputStream's bigNumberMode is not set to a
                //   configuration that will allow large numbers
                ContainerFile("file-1", 300000001, 200, fileBytes),
                ContainerDirectory("some-dir", 300, 400)
            )

            val requestBody by createForEachTest { FilesystemUploadRequestBody(itemsToUpload) }
            val stream by createForEachTest { ByteArrayOutputStream() }

            beforeEachTest { requestBody.writeTo(stream.sink().buffer()) }

            it("provides the correct content type") {
                assertThat(requestBody.contentType(), equalTo("application/x-tar".toMediaType()))
            }

            it("correctly reports the size of the body") {
                assertThat(requestBody.contentLength(), equalTo(stream.size().toLong()))
            }

            it("includes the details of the provided files and directories") {
                val archive = TarFile(stream.toByteArray())

                assertThat(archive.entries, hasSize(equalTo(2)))

                assertThat(
                    archive.entries,
                    anyElement(
                        hasName("file-1") and hasUID(300000001) and hasGID(200) and isRegularFile() and hasEntrySize(fileBytes.size.toLong())
                    )
                )

                assertThat(archive.readFileContent("file-1").toString(Charsets.UTF_8), equalTo(fileContent))

                assertThat(
                    archive.entries,
                    anyElement(
                        hasName("some-dir/") and hasUID(300) and hasGID(400) and isDirectory()
                    )
                )
            }
        }
    }
})

private fun hasName(expectedName: String) = has("name", TarArchiveEntry::getName, equalTo(expectedName))
private fun hasUID(expectedUID: Long) = has("UID", TarArchiveEntry::getLongUserId, equalTo(expectedUID))
private fun hasGID(expectedGID: Long) = has("GID", TarArchiveEntry::getLongGroupId, equalTo(expectedGID))
private fun isDirectory() = hasDirectoryFlag(true) and hasMode(TarArchiveEntry.DEFAULT_DIR_MODE) and hasEntrySize(0)
private fun isRegularFile() = hasDirectoryFlag(false) and hasMode(TarArchiveEntry.DEFAULT_FILE_MODE)
private fun hasDirectoryFlag(expectedDirectory: Boolean) = has("directory flag", TarArchiveEntry::isDirectory, equalTo(expectedDirectory))
private fun hasMode(expectedMode: Int) = has("mode", TarArchiveEntry::getMode, equalTo(expectedMode))
private fun hasEntrySize(expectedSize: Long) = has("size", TarArchiveEntry::getSize, equalTo(expectedSize))

private fun TarFile.readFileContent(name: String): ByteArray {
    val entry = entries.single { it.name == name }

    getInputStream(entry).use { stream ->
        return stream.readAllBytes()
    }
}
