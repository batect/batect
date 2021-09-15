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
import batect.docker.ContainerFilesystemItem
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.ByteArrayOutputStream

data class FilesystemUploadRequestBody(val contents: Set<ContainerFilesystemItem>) : RequestBody() {
    private val body: ByteArray = createBody()

    private fun createBody(): ByteArray {
        val output = ByteArrayOutputStream()

        TarArchiveOutputStream(output).use { archive ->
            archive.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            // Required to support a uid larger than 2097151
            archive.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            contents.forEach { archive.add(it) }
        }

        return output.toByteArray()
    }

    private fun TarArchiveOutputStream.add(item: ContainerFilesystemItem) {
        val path = if (item is ContainerDirectory) item.path + "/" else item.path
        val entry = TarArchiveEntry(path)
        entry.setUserId(item.uid)
        entry.setGroupId(item.gid)

        if (item is ContainerFile) {
            entry.size = item.contents.size.toLong()
        }

        putArchiveEntry(entry)

        if (item is ContainerFile) {
            write(item.contents)
        }

        closeArchiveEntry()
    }

    override fun contentType(): MediaType = "application/x-tar".toMediaType()
    override fun contentLength(): Long = body.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        sink.write(body).flush()
    }
}
