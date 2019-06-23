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

package batect.os.unix

import batect.os.NativeMethodException
import batect.os.NativeMethods
import batect.os.NoConsoleException
import batect.os.PossiblyUnsupportedValue
import batect.ui.Dimensions
import jnr.constants.platform.Errno
import jnr.ffi.LibraryLoader
import jnr.ffi.Platform
import jnr.ffi.Runtime
import jnr.ffi.Struct
import jnr.ffi.annotations.Out
import jnr.ffi.annotations.SaveError
import jnr.ffi.annotations.Transient
import jnr.posix.POSIX

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

    private val STDIN_FILENO = 0

    override fun getConsoleDimensions(): Dimensions {
        val size = WindowSize(runtime)
        val result = libc.ioctl(STDIN_FILENO, TIOCGWINSZ.getForPlatform(platform), size)

        if (result != 0) {
            val error = Errno.valueOf(posix.errno().toLong())

            if (error == Errno.ENOTTY) {
                throw NoConsoleException()
            }

            throw UnixNativeMethodException("ioctl", error)
        }

        return Dimensions(size.ws_row.get(), size.ws_col.get())
    }

    override fun getUserId(): PossiblyUnsupportedValue<Int> = PossiblyUnsupportedValue.Supported(posix.geteuid())
    override fun getGroupId(): PossiblyUnsupportedValue<Int> = PossiblyUnsupportedValue.Supported(posix.getegid())
    override fun getUserName(): String = posix.getpwuid(posix.geteuid()).loginName
    override fun getGroupName(): PossiblyUnsupportedValue<String> = PossiblyUnsupportedValue.Supported(posix.getgrgid(posix.getegid()).name)

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

class UnixNativeMethodException(method: String, val error: Errno) : NativeMethodException(method, error.name, error.description())
