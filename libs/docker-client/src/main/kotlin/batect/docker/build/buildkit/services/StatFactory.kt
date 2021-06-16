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

package batect.docker.build.buildkit.services

import batect.os.OperatingSystem
import batect.os.SystemInfo
import batect.os.UnixNativeMethodException
import fsutil.types.Stat
import jnr.constants.platform.Errno
import jnr.ffi.LibraryLoader
import jnr.ffi.annotations.Out
import jnr.ffi.annotations.SaveError
import jnr.posix.POSIX
import jnr.posix.POSIXFactory
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.UserDefinedFileAttributeView
import java.util.concurrent.TimeUnit

// This is based on stat.go, stat_unix.go and stat_windows.go from github.com/tonistiigi/fsutil, which is what BuildKit uses internally.
//
// Some shortcuts have been taken, for example:
// - character and block devices are not handled correctly (devmajor and devminor are never populated)
// - there is no handling of hard links (and it looks like Docker doesn't handle these either)
//
// On Windows, Docker does the following, so we do the same:
// - files' and directories' permission bits are always rwxr-xr-x regardless of file system permissions (other mode bits such as type are preserved)
// - uid, gid, devmajor and devminor are always 0
// - xattrs is always empty

class StatFactory(
    private val systemInfo: SystemInfo,
    private val posix: POSIX
) {
    private val extendedAttributesProvider = when (systemInfo.operatingSystem) {
        OperatingSystem.Linux -> LinuxExtendedAttributesProvider()
        OperatingSystem.Mac -> DarwinExtendedAttributesProvider(posix)
        OperatingSystem.Windows -> WindowsExtendedAttributesProvider()
        else -> throw UnsupportedOperationException("Unsupported operating system: ${systemInfo.operatingSystem}")
    }

    fun createStat(path: Path, relativePath: String): Stat {
        val details = posix.lstat(path.toString())
        val linkTarget = if (details.isSymlink) posix.readlink(path.toString()) else ""

        // jnr-posix doesn't expose the mtime_nsec field, so we can't get the modification time
        // with nanosecond precision (https://stackoverflow.com/a/7206128)... so we have to use
        // the JVM's method, which does provide that level of precision. Yuck.
        val modificationTime = Files.getLastModifiedTime(path).to(TimeUnit.NANOSECONDS)

        return Stat(
            relativePath,
            details.mode(),
            details.uid(),
            details.gid(),
            details.st_size(),
            modificationTime,
            linkTarget,
            0,
            0,
            extendedAttributesProvider.getExtendedAttributes(path)
        )
    }

    private interface ExtendedAttributesProvider {
        fun getExtendedAttributes(path: Path): Map<String, ByteString>
    }

    private class LinuxExtendedAttributesProvider : ExtendedAttributesProvider {
        override fun getExtendedAttributes(path: Path): Map<String, ByteString> {
            val attributeView = Files.getFileAttributeView(path, UserDefinedFileAttributeView::class.java)

            if (attributeView == null) {
                throw UnsupportedOperationException("Extended file attributes not supported.")
            }

            return attributeView.list().associateWith { name -> attributeView.getAttributeValue(name) }
        }

        private fun UserDefinedFileAttributeView.getAttributeValue(name: String): ByteString {
            val size = this.size(name)
            val buffer = ByteBuffer.allocate(size)
            this.read(name, buffer)

            return buffer.array().toByteString()
        }
    }

    private class WindowsExtendedAttributesProvider : ExtendedAttributesProvider {
        override fun getExtendedAttributes(path: Path): Map<String, ByteString> {
            return emptyMap()
        }
    }

    private class DarwinExtendedAttributesProvider(private val posix: POSIX) : ExtendedAttributesProvider {
        private val xAttrMethods = LibraryLoader.create(DarwinXAttrMethods::class.java).load(POSIXFactory.STANDARD_C_LIBRARY_NAME)
        private val XATTR_NOFOLLOW = 0x1

        override fun getExtendedAttributes(path: Path): Map<String, ByteString> {
            return getAttributeNames(path).associateWith { getAttributeValue(path, it) }
        }

        private fun getAttributeNames(path: Path): Set<String> {
            val bufferSize = getAttributeListSize(path)

            if (bufferSize == 0L) {
                return emptySet()
            }

            val buffer = ByteBuffer.allocate(bufferSize.toInt())
            val result = xAttrMethods.listxattr(path.toString(), buffer, bufferSize, XATTR_NOFOLLOW)

            if (result < 0) {
                val errno = posix.errno().toLong()

                throw UnixNativeMethodException(DarwinXAttrMethods::listxattr.name, Errno.valueOf(errno))
            }

            return buffer.array()
                .toString(Charsets.UTF_8)
                .removeSuffix("\u0000")
                .split('\u0000')
                .toSet()
        }

        private fun getAttributeListSize(path: Path): Long {
            val size = xAttrMethods.listxattr(path.toString(), null, 0, XATTR_NOFOLLOW)

            if (size < 0) {
                val errno = posix.errno().toLong()

                throw UnixNativeMethodException(DarwinXAttrMethods::listxattr.name, Errno.valueOf(errno))
            }

            return size
        }

        private fun getAttributeValue(path: Path, name: String): ByteString {
            val bufferSize = getAttributeValueSize(path, name)

            if (bufferSize == 0L) {
                return ByteString.EMPTY
            }

            val buffer = ByteBuffer.allocate(bufferSize.toInt())
            val result = xAttrMethods.getxattr(path.toString(), name, buffer, bufferSize, 0, XATTR_NOFOLLOW)

            if (result < 0) {
                val errno = posix.errno().toLong()

                throw UnixNativeMethodException(DarwinXAttrMethods::getxattr.name, Errno.valueOf(errno))
            }

            return buffer.toByteString()
        }

        private fun getAttributeValueSize(path: Path, name: String): Long {
            val size = xAttrMethods.getxattr(path.toString(), name, null, 0, 0, XATTR_NOFOLLOW)

            if (size < 0) {
                val errno = posix.errno().toLong()

                throw UnixNativeMethodException(DarwinXAttrMethods::getxattr.name, Errno.valueOf(errno))
            }

            return size
        }
    }

    interface DarwinXAttrMethods {
        @SaveError
        fun listxattr(path: String, @Out list: ByteBuffer?, size: Long, options: Int): Long

        @SaveError
        fun getxattr(path: String, name: String, @Out value: ByteBuffer?, size: Long, position: Long, options: Int): Long
    }
}
