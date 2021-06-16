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

import batect.os.UnixNativeMethodException
import batect.os.throwWindowsNativeMethodFailed
import batect.os.windows.WindowsNativeMethods
import fsutil.types.Stat
import jnr.constants.platform.Errno
import jnr.ffi.LibraryLoader
import jnr.ffi.LibraryOption
import jnr.ffi.Platform
import jnr.ffi.Runtime
import jnr.ffi.Struct
import jnr.ffi.annotations.Out
import jnr.ffi.annotations.SaveError
import jnr.posix.FileStat
import jnr.posix.HANDLE
import jnr.posix.POSIX
import jnr.posix.POSIXFactory
import jnr.posix.WString
import jnr.posix.WindowsLibC
import jnr.posix.util.WindowsHelpers
import jnr.posix.windows.WindowsFindData
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.nio.ByteBuffer
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.LinkOption
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

interface StatFactory {
    fun createStat(path: Path, relativePath: String): Stat

    companion object {
        fun create(posix: POSIX): StatFactory = when (Platform.getNativePlatform().os) {
            Platform.OS.DARWIN -> MacOSStatFactory(posix)
            Platform.OS.LINUX -> LinuxStatFactory(posix)
            Platform.OS.WINDOWS -> WindowsStatFactory(posix)
            else -> throw java.lang.UnsupportedOperationException("Unknown operating system ${Platform.getNativePlatform().os}")
        }
    }
}

abstract class PosixStatFactory(private val posix: POSIX) : StatFactory {
    override fun createStat(path: Path, relativePath: String): Stat {
        val details = posix.lstat(path.toString())
        val linkTarget = if (details.isSymlink) posix.readlink(path.toString()) else ""
        val extendedAttributes = getExtendedAttributes(path)

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
            extendedAttributes
        )
    }

    protected abstract fun getExtendedAttributes(path: Path): Map<String, ByteString>
}

class MacOSStatFactory(private val posix: POSIX) : PosixStatFactory(posix) {
    private val xAttrMethods = LibraryLoader.create(MacOSXAttrMethods::class.java).load(POSIXFactory.STANDARD_C_LIBRARY_NAME)
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

            throw UnixNativeMethodException(MacOSXAttrMethods::listxattr.name, Errno.valueOf(errno))
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

            throw UnixNativeMethodException(MacOSXAttrMethods::listxattr.name, Errno.valueOf(errno))
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

            throw UnixNativeMethodException(MacOSXAttrMethods::getxattr.name, Errno.valueOf(errno))
        }

        return buffer.toByteString()
    }

    private fun getAttributeValueSize(path: Path, name: String): Long {
        val size = xAttrMethods.getxattr(path.toString(), name, null, 0, 0, XATTR_NOFOLLOW)

        if (size < 0) {
            val errno = posix.errno().toLong()

            throw UnixNativeMethodException(MacOSXAttrMethods::getxattr.name, Errno.valueOf(errno))
        }

        return size
    }

    interface MacOSXAttrMethods {
        @SaveError
        fun listxattr(path: String, @Out list: ByteBuffer?, size: Long, options: Int): Long

        @SaveError
        fun getxattr(path: String, name: String, @Out value: ByteBuffer?, size: Long, position: Long, options: Int): Long
    }
}

class LinuxStatFactory(private val posix: POSIX) : PosixStatFactory(posix) {
    override fun getExtendedAttributes(path: Path): Map<String, ByteString> {
        val attributeView = Files.getFileAttributeView(path, UserDefinedFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)

        if (attributeView == null) {
            throw UnsupportedOperationException("Extended file attributes not supported.")
        }

        try {
            return attributeView.list().associateWith { name -> attributeView.getAttributeValue(name) }
        } catch (e: FileSystemException) {
            val message = e.message

            if (message != null && message.endsWith("Too many levels of symbolic links or unable to access attributes of symbolic link")) {
                return emptyMap()
            }

            throw e
        }
    }

    private fun UserDefinedFileAttributeView.getAttributeValue(name: String): ByteString {
        val size = this.size(name)
        val buffer = ByteBuffer.allocate(size)
        this.read(name, buffer)

        return buffer.array().toByteString()
    }
}

class WindowsStatFactory(private val posix: POSIX) : StatFactory {
    private val windowsMethods = LibraryLoader.create(WindowsMethods::class.java)
        .option(LibraryOption.TypeMapper, WindowsNativeMethods.createTypeMapper())
        .library(POSIXFactory.STANDARD_C_LIBRARY_NAME)
        .library("kernel32")
        .load()

    override fun createStat(path: Path, relativePath: String): Stat {
        val details = posix.lstat(path.toString())
        val pathIsSymlink = isSymlink(path)
        val linkTarget = if (pathIsSymlink) symlinkTarget(path) else ""

        // jnr-posix doesn't expose the mtime_nsec field, so we can't get the modification time
        // with nanosecond precision (https://stackoverflow.com/a/7206128)... so we have to use
        // the JVM's method, which does provide that level of precision. Yuck.
        val modificationTime = Files.getLastModifiedTime(path).to(TimeUnit.NANOSECONDS)

        val permissionMask: Int = "777".toInt(8)
        val nonPermissionBits = if (pathIsSymlink) FileStat.S_IFLNK else details.mode() and permissionMask.inv()
        val newMode = nonPermissionBits or "755".toInt(8)

        return Stat(
            relativePath,
            newMode,
            0,
            0,
            details.st_size(),
            modificationTime,
            linkTarget,
            0,
            0,
            emptyMap()
        )
    }

    // The symlink bit is not set correctly by POSIX.stat(), so we have to check for symlinks ourselves...
    // https://devblogs.microsoft.com/oldnewthing/?p=14963 explains this method.
    private fun isSymlink(path: Path): Boolean {
        val data = WindowsFindData(Runtime.getRuntime(windowsMethods))
        val handle = windowsMethods.FindFirstFileW(WString.path(path.toString(), true), data)

        if (!handle.isValid) {
            throwWindowsNativeMethodFailed("FindFirstFileW", posix)
        }

        try {
            return data.fileAttributes and FILE_ATTRIBUTES_REPARSE_POINT != 0 && data.reparsePointTag == IO_REPARSE_TAG_SYMLINK
        } finally {
            val result = windowsMethods.FindClose(handle)

            if (result == 0) {
                throwWindowsNativeMethodFailed(WindowsMethods::FindClose, posix)
            }
        }
    }

    private val WindowsFindData.reparsePointTag: ULong
        get() {
            val field = this::class.java.getDeclaredField("dwReserved0")
            field.isAccessible = true

            val value = (field.get(this) as Struct.UnsignedLong).get().toULong()
            val mask = 0xFFFFFFFF.toULong() // Reparse point tags are 32-bit values (https://docs.microsoft.com/en-us/windows/win32/fileio/reparse-point-tags)
            return value and mask
        }

    // Based on https://stackoverflow.com/a/58644115/1668119
    private fun symlinkTarget(path: Path): String {
        val handle = windowsMethods.CreateFileW(WindowsHelpers.toWPath(path.toString()), GENERIC_READ, FILE_SHARE_READ, null, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS, 0)

        if (!handle.isValid) {
            throwWindowsNativeMethodFailed(WindowsMethods::CreateFileW, posix)
        }

        try {
            val length = windowsMethods.GetFinalPathNameByHandleW(handle, null, 0, FILE_NAME_OPENED)

            if (length == 0) {
                throwWindowsNativeMethodFailed(WindowsMethods::GetFinalPathNameByHandleW, posix)
            }

            val buffer = ByteArray(length * 2)
            val result = windowsMethods.GetFinalPathNameByHandleW(handle, buffer, length, FILE_NAME_OPENED)

            if (result == 0) {
                throwWindowsNativeMethodFailed(WindowsMethods::GetFinalPathNameByHandleW, posix)
            }

            val rawPath = String(buffer, 0, buffer.size - 2, Charsets.UTF_16LE)

            return normalisePath(rawPath)
        } finally {
            windowsMethods.CloseHandle(handle)
        }
    }

    private fun normalisePath(rawPath: String): String {
        return rawPath.removePrefix("""\\?\""")
    }

    companion object {
        private const val FILE_ATTRIBUTES_REPARSE_POINT = 0x400
        private val IO_REPARSE_TAG_SYMLINK = 0xA000000C.toULong()

        private const val GENERIC_READ = 0x80000000.toInt()
        private const val FILE_SHARE_READ = 0x1
        private const val OPEN_EXISTING = 0x3
        private const val FILE_FLAG_BACKUP_SEMANTICS = 0x02000000
        private const val FILE_NAME_OPENED = 0x8
    }

    interface WindowsMethods : WindowsLibC {
        @SaveError
        fun GetFinalPathNameByHandleW(hFile: HANDLE, lpszFilePath: ByteArray?, cchFilePath: Int, dwFlags: Int): Int
    }
}
