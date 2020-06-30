/*
   Copyright 2017-2020 Charles Korn.

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

package batect.os.unix

import batect.os.Dimensions
import batect.os.NativeMethods
import batect.os.NoConsoleException
import batect.os.UnixNativeMethodException
import jnr.constants.platform.Errno
import jnr.ffi.LibraryLoader
import jnr.ffi.Platform
import jnr.ffi.Runtime
import jnr.ffi.Struct
import jnr.ffi.annotations.Out
import jnr.ffi.annotations.SaveError
import jnr.ffi.annotations.Transient
import jnr.posix.POSIX
import java.io.FileDescriptor

class UnixNativeMethods(
    private val libc: LibC,
    private val runtime: Runtime,
    private val platform: Platform,
    private val posix: POSIX
) : NativeMethods {
    constructor(posix: POSIX) : this(
        LibraryLoader.create(LibC::class.java).load("c"),
        Platform.getNativePlatform(),
        posix
    )

    private constructor(libc: LibC, platform: Platform, posix: POSIX) : this(
        libc,
        Runtime.getRuntime(libc),
        platform,
        posix
    )

    private val STDOUT_FILENO = 1

    override fun getConsoleDimensions(): Dimensions {
        val size = WindowSize(runtime)
        val result = libc.ioctl(STDOUT_FILENO, TIOCGWINSZ.getForPlatform(platform), size)

        if (result != 0) {
            val error = Errno.valueOf(posix.errno().toLong())

            if (error == Errno.ENOTTY || error == Errno.ENODEV) {
                throw NoConsoleException()
            }

            throw UnixNativeMethodException(libc::ioctl.name, error)
        }

        return Dimensions(size.ws_row.get(), size.ws_col.get())
    }

    override fun determineIfStdinIsTTY(): Boolean = posix.isatty(FileDescriptor.`in`)
    override fun determineIfStdoutIsTTY(): Boolean = posix.isatty(FileDescriptor.out)
    override fun determineIfStderrIsTTY(): Boolean = posix.isatty(FileDescriptor.err)

    override fun getUserId(): Int = posix.geteuid()
    override fun getGroupId(): Int = posix.getegid()

    override fun getUserName(): String {
        val uid = getUserId()
        val user = posix.getpwuid(uid)

        if (user == null) {
            val errno = posix.errno().toLong()

            if (errno == 0L) {
                throw RuntimeException("User with UID $uid does not exist.")
            }

            throw UnixNativeMethodException(posix::getpwuid.name, Errno.valueOf(errno))
        }

        return user.loginName
    }

    override fun getGroupName(): String {
        val gid = getGroupId()
        val group = posix.getgrgid(gid)

        if (group == null) {
            val errno = posix.errno().toLong()

            if (errno == 0L) {
                throw RuntimeException("Group with GID $gid does not exist.")
            }

            throw UnixNativeMethodException(posix::getgrgid.name, Errno.valueOf(errno))
        }

        return group.name
    }

    private val TIOCGWINSZ = PlatformSpecificConstant(darwinValue = 0x40087468, linuxValue = 0x00005413)

    interface LibC {
        @SaveError
        fun ioctl(fd: Int, request: Int, @Out @Transient winsize: WindowSize): Int
    }

    // Native name is winsize
    class WindowSize(runtime: Runtime) : Struct(runtime) {
        val ws_row = Unsigned16()
        val ws_col = Unsigned16()
        val ws_xpixel = Unsigned16()
        val ws_ypixel = Unsigned16()

        override fun toString(): kotlin.String {
            return "Rows: $ws_row, cols: $ws_col"
        }
    }
}

data class PlatformSpecificConstant<T>(val darwinValue: T, val linuxValue: T) {
    constructor(valueForAllPlatforms: T) : this(valueForAllPlatforms, valueForAllPlatforms)

    fun getForPlatform(platform: Platform): T = when (platform.os) {
        Platform.OS.DARWIN -> darwinValue
        Platform.OS.LINUX -> linuxValue
        else -> throw UnsupportedOperationException("The platform ${platform.os.name} is not supported.")
    }
}
