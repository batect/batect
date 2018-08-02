/*
   Copyright 2017-2018 Charles Korn.

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

package batect.os

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

class NativeMethods(
    private val libc: LibC,
    private val runtime: Runtime,
    private val platform: Platform,
    private val posix: POSIX
) {
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

    fun getConsoleDimensions(): Dimensions {
        val size = WindowSize(runtime)
        val result = libc.ioctl(0, TIOCGWINSZ, size)

        if (result != 0) {
            val error = Errno.valueOf(posix.errno().toLong())
            throw NativeMethodException("ioctl", error)
        }

        return Dimensions(size.ws_row.get(), size.ws_col.get())
    }

    private val TIOCGWINSZ: Int by lazy {
        when (platform.os) {
            Platform.OS.DARWIN -> 0x40087468
            Platform.OS.LINUX -> 0x00005413
            else -> throw UnsupportedOperationException("The platform ${platform.os.name} is not supported.")
        }
    }

    interface LibC {
        @SaveError
        fun ioctl(fd: Int, request: Int, @Out @Transient winsize: WindowSize): Int
    }

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

class NativeMethodException(val method: String, val error: Errno) : RuntimeException("Invoking native method $method failed with error ${error.name} (${error.description()}).")
