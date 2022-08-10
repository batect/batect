/*
    Copyright 2017-2022 Charles Korn.

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

import batect.os.Dimensions
import batect.os.NativeMethods
import batect.os.NoConsoleException
import batect.os.throwWindowsNativeMethodFailed
import jnr.ffi.LibraryLoader
import jnr.ffi.LibraryOption
import jnr.ffi.Runtime
import jnr.ffi.Struct
import jnr.ffi.annotations.In
import jnr.ffi.annotations.Out
import jnr.ffi.annotations.SaveError
import jnr.ffi.annotations.StdCall
import jnr.ffi.annotations.Transient
import jnr.ffi.byref.IntByReference
import jnr.ffi.mapper.TypeMapper
import jnr.posix.HANDLE
import jnr.posix.POSIX
import jnr.posix.POSIXFactory
import jnr.posix.WindowsLibC
import java.io.FileDescriptor
import java.nio.ByteBuffer

class WindowsNativeMethods(
    private val posix: POSIX,
    private val win32: Win32,
    private val runtime: Runtime
) : NativeMethods {
    constructor(posix: POSIX) : this(
        LibraryLoader.create(Win32::class.java)
            .option(LibraryOption.TypeMapper, createTypeMapper())
            .library(POSIXFactory.STANDARD_C_LIBRARY_NAME)
            .library("kernel32")
            .library("Advapi32")
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

        if (!console.isValid) {
            throwWindowsNativeMethodFailed(Win32::GetStdHandle, posix)
        }

        val info = ConsoleScreenBufferInfo(runtime)
        val succeeded = win32.GetConsoleScreenBufferInfo(console, info)

        if (!succeeded) {
            val errno = posix.errno()

            if (errno == ERROR_INVALID_HANDLE) {
                throw NoConsoleException()
            }

            throwWindowsNativeMethodFailed(Win32::GetConsoleScreenBufferInfo, posix)
        }

        val height = info.srWindow.bottom.intValue() - info.srWindow.top.intValue() + 1
        val width = info.srWindow.right.intValue() - info.srWindow.left.intValue() + 1

        return Dimensions(height, width)
    }

    override fun determineIfStdinIsTTY(): Boolean = isTTY(FileDescriptor.`in`, WindowsLibC.STD_INPUT_HANDLE)
    override fun determineIfStdoutIsTTY(): Boolean = isTTY(FileDescriptor.out, WindowsLibC.STD_OUTPUT_HANDLE)
    override fun determineIfStderrIsTTY(): Boolean = isTTY(FileDescriptor.err, WindowsLibC.STD_ERROR_HANDLE)

    // See http://archives.miloush.net/michkap/archive/2008/03/18/8306597.140100.html for an explanation of this.
    private fun isTTY(fd: FileDescriptor, stdHandle: Int): Boolean {
        if (!posix.isatty(fd)) {
            return false
        }

        val console = win32.GetStdHandle(stdHandle)

        if (!console.isValid) {
            throwWindowsNativeMethodFailed(Win32::GetStdHandle, posix)
        }

        val currentConsoleMode = IntByReference()

        if (!win32.GetConsoleMode(console, currentConsoleMode)) {
            val errno = posix.errno()

            if (errno == ERROR_INVALID_HANDLE) {
                return false
            }

            throwWindowsNativeMethodFailed(Win32::GetConsoleMode, posix)
        }

        return true
    }

    fun enableConsoleEscapeSequences() {
        updateConsoleMode(WindowsLibC.STD_OUTPUT_HANDLE) { currentMode ->
            currentMode or ENABLE_VIRTUAL_TERMINAL_PROCESSING or DISABLE_NEWLINE_AUTO_RETURN
        }
    }

    private fun updateConsoleMode(handle: Int, transform: (Int) -> Int): Int {
        val console = win32.GetStdHandle(handle)

        if (!console.isValid) {
            throwWindowsNativeMethodFailed(Win32::GetStdHandle, posix)
        }

        val currentConsoleMode = IntByReference()

        if (!win32.GetConsoleMode(console, currentConsoleMode)) {
            throwWindowsNativeMethodFailed(Win32::GetConsoleMode, posix)
        }

        val newConsoleMode = transform(currentConsoleMode.toInt())

        if (!win32.SetConsoleMode(console, newConsoleMode)) {
            throwWindowsNativeMethodFailed(Win32::SetConsoleMode, posix)
        }

        return currentConsoleMode.toInt()
    }

    override fun getUserName(): String {
        val bytesPerCharacter = 2
        val maxLengthInCharacters = 256
        val buffer = ByteArray(maxLengthInCharacters * bytesPerCharacter)
        val length = IntByReference(maxLengthInCharacters)

        val succeeded = win32.GetUserNameW(ByteBuffer.wrap(buffer), length)

        if (!succeeded) {
            throwWindowsNativeMethodFailed(Win32::GetUserNameW, posix)
        }

        val bytesReturned = (length.toInt() - 1) * bytesPerCharacter
        return String(buffer, 0, bytesReturned, Charsets.UTF_16LE)
    }

    override fun getUserId(): Int = throw UnsupportedOperationException("Getting the user ID is not supported on Windows.")
    override fun getGroupId(): Int = throw UnsupportedOperationException("Getting the group ID is not supported on Windows.")
    override fun getGroupName(): String = throw UnsupportedOperationException("Getting the group name is not supported on Windows.")

    interface Win32 : WindowsLibC {
        @SaveError
        @StdCall
        fun GetConsoleScreenBufferInfo(
            @In
            hConsoleOutput: HANDLE,

            @Out @Transient
            lpConsoleScreenBufferInfo: ConsoleScreenBufferInfo
        ): Boolean

        @SaveError
        @StdCall
        fun SetConsoleMode(@In hConsoleHandle: HANDLE, @In dwMode: Int): Boolean

        @SaveError
        @StdCall
        fun GetConsoleMode(@In hConsoleHandle: HANDLE, @Out lpMode: IntByReference): Boolean

        @SaveError
        fun GetUserNameW(
            @Out
            lpBuffer: ByteBuffer,

            @In @Out
            pcbBuffer: IntByReference
        ): Boolean
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
        private const val ERROR_INVALID_HANDLE: Int = 0x00000006

        private const val ENABLE_VIRTUAL_TERMINAL_PROCESSING: Int = 0x4
        private const val DISABLE_NEWLINE_AUTO_RETURN: Int = 0x8

        // HACK: This is a hack to workaround the fact that POSIXTypeMapper isn't public, but we
        // need it to translate a number of different Win32 types to their JVM equivalents.
        fun createTypeMapper(): TypeMapper {
            val constructor = Class.forName("jnr.posix.POSIXTypeMapper").getDeclaredConstructor()
            constructor.isAccessible = true

            return constructor.newInstance() as TypeMapper
        }
    }
}
