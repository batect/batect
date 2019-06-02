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

package batect.os.windows

import batect.os.NativeMethodException
import batect.os.NativeMethods
import batect.os.NoConsoleException
import batect.ui.Dimensions
import jnr.constants.platform.windows.LastError
import jnr.ffi.LibraryLoader
import jnr.ffi.LibraryOption
import jnr.ffi.Runtime
import jnr.ffi.Struct
import jnr.ffi.annotations.In
import jnr.ffi.annotations.Out
import jnr.ffi.annotations.SaveError
import jnr.ffi.annotations.StdCall
import jnr.ffi.annotations.Transient
import jnr.ffi.mapper.TypeMapper
import jnr.posix.HANDLE
import jnr.posix.POSIX
import jnr.posix.WindowsLibC

class WindowsNativeMethods(
    private val posix: POSIX,
    private val win32: Win32,
    private val runtime: Runtime
) : NativeMethods {
    constructor(posix: POSIX) : this(
        LibraryLoader.create(Win32::class.java)
            .option(LibraryOption.TypeMapper, createTypeMapper())
            .library("msvcrt")
            .library("kernel32")
            .load(),
        posix
    )

    constructor(win32: Win32, posix: POSIX) : this(
        posix,
        win32,
        Runtime.getRuntime(win32)
    )

    override fun getConsoleDimensions(): Dimensions {
        val console = win32.GetStdHandle(WindowsLibC.STD_OUTPUT_HANDLE)
        val info = ConsoleScreenBufferInfo(runtime)
        val result = win32.GetConsoleScreenBufferInfo(console, info)

        val height = info.srWindow.bottom.intValue() - info.srWindow.top.intValue() + 1
        val width = info.srWindow.right.intValue() - info.srWindow.left.intValue() + 1

        if (result == 0) {
            val errno = posix.errno()
            val error = LastError.values().single { it.intValue() == errno }

            if (error == LastError.ERROR_INVALID_HANDLE) {
                throw NoConsoleException()
            }

            throw WindowsNativeMethodException(Win32::GetConsoleScreenBufferInfo.name, error)
        }

        return Dimensions(height, width)
    }

    interface Win32 : WindowsLibC {
        @SaveError
        @StdCall
        fun GetConsoleScreenBufferInfo(@In hConsoleOutput: HANDLE, @Out @Transient lpConsoleScreenBufferInfo: ConsoleScreenBufferInfo): Int
    }

    class ConsoleScreenBufferInfo(runtime: Runtime) : Struct(runtime) {
        val dwSize = inner(Coord(runtime))
        val dwCursorPosition = inner(Coord(runtime))
        val wAttributes = WORD()
        val srWindow = inner(SmallRect(runtime))
        val dwMaximumWindowSize = inner(Coord(runtime))
    }

    class Coord(runtime: Runtime) : Struct(runtime) {
        val x = int16_t()
        val y = int16_t()
    }

    class SmallRect(runtime: Runtime) : Struct(runtime) {
        val left = int16_t()
        val top = int16_t()
        val right = int16_t()
        val bottom = int16_t()
    }

    companion object {
        // HACK: This is a hack to workaround the fact that POSIXTypeMapper isn't public, but we
        // need it to translate a number of different Win32 types to their JVM equivalents.
        private fun createTypeMapper(): TypeMapper {
            val constructor = Class.forName("jnr.posix.POSIXTypeMapper").getDeclaredConstructor()
            constructor.isAccessible = true

            return constructor.newInstance() as TypeMapper
        }
    }
}

class WindowsNativeMethodException(method: String, val error: LastError) :
    NativeMethodException(method, error.name, error.toString())
