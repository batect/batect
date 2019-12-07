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

import jnr.ffi.Platform
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption

data class DockerImageBuildContextRequestBody(val context: DockerImageBuildContext) : RequestBody() {
    override fun contentType(): MediaType = "application/x-tar".toMediaType()

    override fun writeTo(sink: BufferedSink) {
        TarArchiveOutputStream(sink.outputStream()).use { output ->
            output.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)

            context.entries.forEach { entry ->
                writeEntry(entry, output)
            }
        }
    }

    private fun writeEntry(entry: DockerImageBuildContextEntry, output: TarArchiveOutputStream) {
        val archiveEntry = if (Files.isSymbolicLink(entry.localPath)) {
            createSymbolicLinkEntry(entry)
        } else {
            createNormalEntry(entry)
        }

        if (Platform.getNativePlatform().os == Platform.OS.WINDOWS) {
            archiveEntry.mode = 0b111_101_101
        } else {
            archiveEntry.mode = Files.getAttribute(entry.localPath, "unix:mode", LinkOption.NOFOLLOW_LINKS) as Int
        }

        output.putArchiveEntry(archiveEntry)

        if (Files.isRegularFile(entry.localPath, LinkOption.NOFOLLOW_LINKS)) {
            entry.localPath.toFile().inputStream().use { reader ->
                reader.copyTo(output)
            }
        }

        output.closeArchiveEntry()
    }

    private fun createNormalEntry(entry: DockerImageBuildContextEntry) = TarArchiveEntry(entry.localPath.toFile(), entry.contextPath)

    private fun createSymbolicLinkEntry(entry: DockerImageBuildContextEntry): TarArchiveEntry {
        val archiveEntry = TarArchiveEntry(entry.contextPath, TarArchiveEntry.LF_SYMLINK)
        val target = Files.readSymbolicLink(entry.localPath)

        archiveEntry.linkName = if (target.isAbsolute) {
            entry.localPath.parent.relativize(target).toString()
        } else {
            target.toString()
        }

        return archiveEntry
    }
}
