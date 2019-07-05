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

import batect.os.Dimensions
import batect.os.NativeMethodException
import batect.os.NativeMethods
import batect.os.NoConsoleException
import batect.os.PossiblyUnsupportedValue
import batect.os.windows.namedpipes.NamedPipe
import jnr.constants.platform.windows.LastError
import jnr.ffi.LibraryLoader
import jnr.ffi.LibraryOption
import jnr.ffi.Pointer
import jnr.ffi.Runtime
import jnr.ffi.Struct
import jnr.ffi.annotations.Direct
import jnr.ffi.annotations.In
import jnr.ffi.annotations.Out
import jnr.ffi.annotations.SaveError
import jnr.ffi.annotations.StdCall
import jnr.ffi.annotations.Transient
import jnr.ffi.byref.IntByReference
import jnr.ffi.byref.NativeLongByReference
import jnr.ffi.mapper.TypeMapper
import jnr.posix.HANDLE
import jnr.posix.POSIX
import jnr.posix.WindowsLibC
import jnr.posix.util.WindowsHelpers
import java.io.FileNotFoundException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import kotlin.reflect.KFunction

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
            throwNativeMethodFailed(Win32::GetStdHandle)
        }

        val info = ConsoleScreenBufferInfo(runtime)
        val succeeded = win32.GetConsoleScreenBufferInfo(console, info)

        if (!succeeded) {
            val errno = posix.errno()
            val error = LastError.values().single { it.intValue() == errno }

            if (error == LastError.ERROR_INVALID_HANDLE) {
                throw NoConsoleException()
            }

            throw WindowsNativeMethodException(Win32::GetConsoleScreenBufferInfo.name, error)
        }

        val height = info.srWindow.bottom.intValue() - info.srWindow.top.intValue() + 1
        val width = info.srWindow.right.intValue() - info.srWindow.left.intValue() + 1

        return Dimensions(height, width)
    }

    fun enableConsoleEscapeSequences() {
        updateConsoleMode(WindowsLibC.STD_OUTPUT_HANDLE) { currentMode ->
            currentMode or ENABLE_VIRTUAL_TERMINAL_PROCESSING or DISABLE_NEWLINE_AUTO_RETURN
        }
    }

    // This is based on MakeRaw from term_windows.go in the Docker CLI.
    fun enableConsoleRawMode(): Int = updateConsoleMode(WindowsLibC.STD_INPUT_HANDLE) { currentMode ->
        (
            currentMode
                or ENABLE_EXTENDED_FLAGS
                or ENABLE_INSERT_MODE
                or ENABLE_QUICK_EDIT_MODE
                or ENABLE_VIRTUAL_TERMINAL_INPUT
                and ENABLE_ECHO_INPUT.inv()
                and ENABLE_LINE_INPUT.inv()
                and ENABLE_MOUSE_INPUT.inv()
                and ENABLE_WINDOW_INPUT.inv()
                and ENABLE_PROCESSED_INPUT.inv()
            )
    }

    private fun updateConsoleMode(handle: Int, transform: (Int) -> Int): Int {
        val console = win32.GetStdHandle(handle)

        if (!console.isValid) {
            throwNativeMethodFailed(Win32::GetStdHandle)
        }

        val currentConsoleMode = IntByReference()

        if (!win32.GetConsoleMode(console, currentConsoleMode)) {
            throwNativeMethodFailed(Win32::GetConsoleMode)
        }

        val newConsoleMode = transform(currentConsoleMode.toInt())

        if (!win32.SetConsoleMode(console, newConsoleMode)) {
            throwNativeMethodFailed(Win32::SetConsoleMode)
        }

        return currentConsoleMode.toInt()
    }

    fun restoreConsoleMode(previousMode: Int) {
        val console = win32.GetStdHandle(WindowsLibC.STD_INPUT_HANDLE)

        if (!console.isValid) {
            throwNativeMethodFailed(Win32::GetStdHandle)
        }

        if (!win32.SetConsoleMode(console, previousMode)) {
            throwNativeMethodFailed(Win32::SetConsoleMode)
        }
    }

    override fun getUserName(): String {
        val bytesPerCharacter = 2
        val maxLengthInCharacters = 256
        val buffer = ByteArray(maxLengthInCharacters * bytesPerCharacter)
        val length = IntByReference(maxLengthInCharacters)

        val succeeded = win32.GetUserNameW(ByteBuffer.wrap(buffer), length)

        if (!succeeded) {
            throwNativeMethodFailed(Win32::GetUserNameW)
        }

        val bytesReturned = (length.toInt() - 1) * bytesPerCharacter
        return String(buffer, 0, bytesReturned, Charsets.UTF_16LE)
    }

    override fun getUserId(): PossiblyUnsupportedValue<Int> = PossiblyUnsupportedValue.Unsupported("Getting the user ID is not supported on Windows.")
    override fun getGroupId(): PossiblyUnsupportedValue<Int> = PossiblyUnsupportedValue.Unsupported("Getting the group ID is not supported on Windows.")
    override fun getGroupName(): PossiblyUnsupportedValue<String> = PossiblyUnsupportedValue.Unsupported("Getting the group name is not supported on Windows.")

    fun openNamedPipe(path: String, connectionTimeoutInMilliseconds: Int): NamedPipe {
        val startTime = System.nanoTime()

        do {
            val result = win32.CreateFileW(WindowsHelpers.toWPath(path), GENERIC_READ or GENERIC_WRITE, 0L, null, OPEN_EXISTING, FILE_FLAG_OVERLAPPED, null)

            if (!result.isValid) {
                if (posix.errno() == ERROR_FILE_NOT_FOUND) {
                    throw FileNotFoundException("The named pipe $path does not exist.")
                }

                if (posix.errno() == ERROR_PIPE_BUSY) {
                    continue
                }

                throwNativeMethodFailed(WindowsLibC::CreateFileW)
            }

            return NamedPipe(result, this)
        } while (!hasTimedOut(startTime, connectionTimeoutInMilliseconds))

        throw SocketTimeoutException("Could not connect to $path within $connectionTimeoutInMilliseconds milliseconds.")
    }

    private fun hasTimedOut(startTime: Long, timeoutInMilliseconds: Int): Boolean {
        if (timeoutInMilliseconds == 0) {
            return false
        }

        val elapsedTime = System.nanoTime() - startTime

        return elapsedTime > timeoutInMilliseconds * 1000
    }

    fun closeNamedPipe(pipe: NamedPipe) {
        cancelIo(pipe, null)

        closeHandle(pipe.handle)
    }

    private fun closeHandle(handle: HANDLE) {
        if (!win32.CloseHandle(handle)) {
            throwNativeMethodFailed(Win32::CloseHandle)
        }
    }

    fun writeToNamedPipe(pipe: NamedPipe, buffer: ByteArray, offset: Int, length: Int) {
        val event = createEvent()

        try {
            val overlapped = Overlapped(runtime)
            overlapped.event.set(event.toPointer())

            val bufferPointer = runtime.memoryManager.allocateDirect(length, true)
            bufferPointer.put(0, buffer, offset, length)

            startWrite(pipe, bufferPointer, overlapped)

            val bytesWritten = waitForOverlappedOperation(pipe, overlapped, event, WindowsLibC.INFINITE)

            if (bytesWritten != length) {
                throw RuntimeException("Expected to write $length bytes, but wrote $bytesWritten")
            }
        } finally {
            closeHandle(event)
        }
    }

    private fun startWrite(pipe: NamedPipe, buffer: Pointer, overlapped: Overlapped) {
        if (!win32.WriteFile(pipe.handle, buffer, buffer.size(), null, overlapped)) {
            throwNativeMethodFailed(Win32::WriteFile)
        }
    }

    fun readFromNamedPipe(pipe: NamedPipe, buffer: ByteArray, offset: Int, maxLength: Int, timeoutInMilliseconds: Int): Int {
        val event = createEvent()

        try {
            val overlapped = Overlapped(runtime)
            overlapped.event.set(event.toPointer())

            val bufferPointer = runtime.memoryManager.allocateDirect(maxLength, true)

            if (!startRead(pipe, bufferPointer, overlapped)) {
                return -1
            }

            val bytesRead = waitForOverlappedOperation(pipe, overlapped, event, translateTimeout(timeoutInMilliseconds))
            bufferPointer.get(0, buffer, offset, bytesRead)

            if (bytesRead == 0) {
                return -1
            }

            return bytesRead
        } finally {
            closeHandle(event)
        }
    }

    private fun translateTimeout(timeoutInMilliseconds: Int): Int = if (timeoutInMilliseconds == 0) {
        WindowsLibC.INFINITE
    } else {
        timeoutInMilliseconds
    }

    private fun startRead(pipe: NamedPipe, buffer: Pointer, overlapped: Overlapped): Boolean {
        if (win32.ReadFile(pipe.handle, buffer, buffer.size(), null, overlapped)) {
            return true
        }

        val errno = posix.errno()

        if (errno == ERROR_IO_PENDING) {
            return true
        }

        val error = LastError.values().single { it.intValue() == errno }

        if (error == LastError.ERROR_BROKEN_PIPE) {
            return false
        }

        throw WindowsNativeMethodException(Win32::ReadFile.name, error)
    }

    private fun waitForOverlappedOperation(pipe: NamedPipe, overlapped: Overlapped, event: HANDLE, timeoutInMilliseconds: Int): Int {
        if (waitForEvent(event, timeoutInMilliseconds) == WaitResult.TimedOut) {
            cancelIo(pipe, overlapped)

            throw SocketTimeoutException("Operation timed out after $timeoutInMilliseconds ms.")
        }

        return getOverlappedResult(pipe, overlapped)
    }

    private fun getOverlappedResult(pipe: NamedPipe, overlapped: Overlapped): Int {
        val bytesTransferred = NativeLongByReference()

        if (!win32.GetOverlappedResult(pipe.handle, overlapped, bytesTransferred, false)) {
            if (posix.errno() == ERROR_OPERATION_ABORTED) {
                return 0
            }

            throwNativeMethodFailed(Win32::GetOverlappedResult)
        }

        return bytesTransferred.toInt()
    }

    private fun createEvent(): HANDLE {
        val result = win32.CreateEventW(null, true, false, null)

        if (!result.isValid) {
            throwNativeMethodFailed(Win32::CreateEventW)
        }

        return result
    }

    private fun waitForEvent(event: HANDLE, timeoutInMilliseconds: Int): WaitResult {
        val result = win32.WaitForSingleObject(event, timeoutInMilliseconds)

        when (result) {
            WAIT_OBJECT_0 -> return WaitResult.Signaled
            WAIT_TIMEOUT -> return WaitResult.TimedOut
            WAIT_ABANDONED -> throw RuntimeException("WaitForSingleObject returned WAIT_ABANDONED")
            else -> throwNativeMethodFailed(Win32::WaitForSingleObject)
        }
    }

    private fun cancelIo(pipe: NamedPipe, overlapped: Overlapped?) {
        if (!win32.CancelIoEx(pipe.handle, overlapped)) {
            if (posix.errno() == ERROR_NOT_FOUND) {
                // There was nothing to cancel.
                return
            }

            throwNativeMethodFailed(Win32::CancelIoEx)
        }
    }

    private fun <R> throwNativeMethodFailed(function: KFunction<R>): Nothing {
        val errno = posix.errno()
        val error = LastError.values().single { it.intValue() == errno }

        throw WindowsNativeMethodException(function.name, error)
    }

    private enum class WaitResult {
        Signaled,
        TimedOut
    }

    interface Win32 : WindowsLibC {
        @SaveError
        @StdCall
        fun GetConsoleScreenBufferInfo(@In hConsoleOutput: HANDLE, @Out @Transient lpConsoleScreenBufferInfo: ConsoleScreenBufferInfo): Boolean

        @SaveError
        @StdCall
        fun SetConsoleMode(@In hConsoleHandle: HANDLE, @In dwMode: Int): Boolean

        @SaveError
        @StdCall
        fun GetConsoleMode(@In hConsoleHandle: HANDLE, @Out lpMode: IntByReference): Boolean

        @SaveError
        fun GetUserNameW(@Out lpBuffer: ByteBuffer, @In @Out pcbBuffer: IntByReference): Boolean

        @StdCall
        fun CreateFileW(
            @In lpFileName: ByteArray,
            @In dwDesiredAccess: Long,
            @In dwShareMode: Long,
            @In lpSecurityAttributes: Pointer?,
            @In dwCreationDisposition: Long,
            @In dwFlagsAndAttributes: Long,
            @In hTemplateFile: HANDLE?
        ): HANDLE

        @SaveError
        fun WriteFile(@In hFile: HANDLE, @Direct lpBuffer: Pointer, @In nNumberOfBytesToWrite: Long, @Out lpNumberOfBytesWritten: NativeLongByReference?, @Direct lpOverlapped: Overlapped): Boolean

        @SaveError
        fun ReadFile(@In hFile: HANDLE, @Direct lpBuffer: Pointer, @In nNumberOfBytesToRead: Long, @Out lpNumberOfBytesRead: NativeLongByReference?, @Direct lpOverlapped: Overlapped): Boolean

        @SaveError
        fun CreateEventW(@In lpEventAttributes: Pointer?, @In bManualReset: Boolean, @In bInitialState: Boolean, @In lpName: ByteArray?): HANDLE

        @SaveError
        fun CancelIoEx(@In hFile: HANDLE, @Direct lpOverlapped: Overlapped?): Boolean

        @SaveError
        fun GetOverlappedResult(@In hFile: HANDLE, @Direct lpOverlapped: Overlapped, @Out lpNumberOfBytesTransferred: NativeLongByReference, @In bWait: Boolean): Boolean
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

    class Overlapped(runtime: Runtime) : Struct(runtime) {
        val internal = UnsignedLong()
        val intervalHigh = UnsignedLong()
        val offsetHigh = DWORD()
        val offsetLow = DWORD()
        val pointer = Pointer()
        val event = Pointer()

        init {
            this.useMemory(runtime.memoryManager.allocateDirect(Struct.size(this), true))
        }
    }

    companion object {
        private const val GENERIC_READ: Long = 0x80000000
        private const val GENERIC_WRITE: Long = 0x40000000
        private const val OPEN_EXISTING: Long = 0x00000003
        private const val FILE_FLAG_OVERLAPPED: Long = 0x40000000

        private const val ERROR_FILE_NOT_FOUND: Int = 0x00000002
        private const val ERROR_IO_PENDING: Int = 0x000003E5
        private const val ERROR_OPERATION_ABORTED: Int = 0x000003E3
        private const val ERROR_NOT_FOUND: Int = 0x00000490
        private const val ERROR_PIPE_BUSY: Int = 0x000000E7

        private const val WAIT_ABANDONED: Int = 0x00000080
        private const val WAIT_OBJECT_0: Int = 0x00000000
        private const val WAIT_TIMEOUT: Int = 0x00000102

        private const val ENABLE_ECHO_INPUT: Int = 0x4
        private const val ENABLE_LINE_INPUT: Int = 0x2
        private const val ENABLE_MOUSE_INPUT: Int = 0x10
        private const val ENABLE_WINDOW_INPUT: Int = 0x8
        private const val ENABLE_PROCESSED_INPUT: Int = 0x1
        private const val ENABLE_EXTENDED_FLAGS: Int = 0x80
        private const val ENABLE_INSERT_MODE: Int = 0x20
        private const val ENABLE_QUICK_EDIT_MODE: Int = 0x40
        private const val ENABLE_VIRTUAL_TERMINAL_INPUT: Int = 0x200

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

class WindowsNativeMethodException(method: String, val error: LastError) :
    NativeMethodException(method, error.name, error.toString())
