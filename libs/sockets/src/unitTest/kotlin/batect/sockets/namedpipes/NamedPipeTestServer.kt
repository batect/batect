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

package batect.sockets.namedpipes

import batect.os.WindowsNativeMethodException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import jnr.constants.platform.windows.LastError
import jnr.ffi.LibraryLoader
import jnr.ffi.LibraryOption
import jnr.ffi.Pointer
import jnr.ffi.Runtime
import jnr.ffi.annotations.Direct
import jnr.ffi.annotations.In
import jnr.ffi.annotations.Out
import jnr.ffi.annotations.SaveError
import jnr.ffi.byref.NativeLongByReference
import jnr.posix.HANDLE
import jnr.posix.POSIX
import jnr.posix.POSIXFactory
import jnr.posix.WindowsLibC
import kotlin.concurrent.thread
import kotlin.reflect.KFunction

class NamedPipeTestServer private constructor(
    val name: String,
    private val posix: POSIX,
    private val win32: Win32,
    private val runtime: Runtime
) : AutoCloseable {
    constructor(name: String) : this(
        name,
        LibraryLoader.create(Win32::class.java)
            .option(LibraryOption.TypeMapper, NativeMethods.createTypeMapper())
            .library("msvcrt")
            .library("kernel32")
            .load(),
        POSIXFactory.getNativePOSIX()
    )

    private constructor(name: String, win32: Win32, posix: POSIX) : this(
        name,
        posix,
        win32,
        Runtime.getRuntime(win32)
    )

    private val pipe: HANDLE
    private val listenerThread: Thread
    private val listenerStartedEvent = Semaphore(0)

    init {
        pipe = win32.CreateNamedPipeA(
            name.toByteArray(Charsets.US_ASCII),
            PIPE_ACCESS_DUPLEX or FILE_FLAG_FIRST_PIPE_INSTANCE,
            PIPE_TYPE_BYTE or PIPE_REJECT_REMOTE_CLIENTS,
            1,
            10000,
            10000,
            0,
            null
        )

        if (!pipe.isValid) {
            throwNativeMethodFailed(Win32::CreateNamedPipeA)
        }

        listenerThread = startListener()

        if (!listenerStartedEvent.tryAcquire(5, TimeUnit.SECONDS)) {
            throw RuntimeException("Listener thread did not start within expected time")
        }
    }

    var sendDelay = 0L
    var dataToSend: String? = null
    var expectData = false
    var dataReceived: String? = null
    val dataReceivedEvent = Semaphore(0)

    private fun startListener(): Thread = thread(isDaemon = true, name = NamedPipeTestServer::class.qualifiedName) {
        listenerStartedEvent.release()
        waitForConnection()

        if (dataToSend != null) {
            Thread.sleep(sendDelay)

            write(dataToSend!!)
        }

        if (expectData) {
            dataReceived = read()
            dataReceivedEvent.release()
        }
    }

    private fun waitForConnection() {
        if (!win32.ConnectNamedPipe(pipe, null)) {
            if (posix.errno() == ERROR_PIPE_CONNECTED) {
                return
            }

            throwNativeMethodFailed(Win32::ConnectNamedPipe)
        }
    }

    private fun write(data: String) {
        val bytes = data.toByteArray()

        if (!win32.WriteFile(pipe, bytes, bytes.size.toLong(), null, null)) {
            throwNativeMethodFailed(Win32::WriteFile)
        }
    }

    private fun read(): String {
        val bytes = ByteArray(1000)
        val numberOfBytesRead = NativeLongByReference()

        if (!win32.ReadFile(pipe, bytes, bytes.size.toLong(), numberOfBytesRead, null)) {
            throwNativeMethodFailed(Win32::ReadFile)
        }

        return String(bytes, 0, numberOfBytesRead.toInt())
    }

    override fun close() {
        val succeeded = win32.CloseHandle(pipe)

        if (!succeeded) {
            throwNativeMethodFailed(Win32::CloseHandle)
        }

        listenerThread.join(500)
    }

    private fun <R> throwNativeMethodFailed(function: KFunction<R>): Nothing {
        val errno = posix.errno()
        val error = LastError.values().single { it.intValue() == errno }

        throw WindowsNativeMethodException(function.name, error)
    }

    interface Win32 : WindowsLibC {
        @SaveError
        fun WriteFile(@In hFile: HANDLE, @Direct lpBuffer: ByteArray, @In nNumberOfBytesToWrite: Long, @Out lpNumberOfBytesWritten: NativeLongByReference?, @Direct lpOverlapped: Pointer?): Boolean

        @SaveError
        fun ReadFile(@In hFile: HANDLE, @Direct lpBuffer: ByteArray, @In nNumberOfBytesToRead: Long, @Out lpNumberOfBytesRead: NativeLongByReference?, @Direct lpOverlapped: Pointer?): Boolean

        @SaveError
        fun CreateNamedPipeA(
            @In lpName: ByteArray,
            @In dwOpenMode: Int,
            @In dwPipeMode: Int,
            @In nMaxInstances: Int,
            @In nOutBufferSize: Int,
            @In nInBufferSize: Int,
            @In nDefaultTimeOut: Int,
            @In lpSecurityAttributes: Pointer?
        ): HANDLE

        @SaveError
        fun ConnectNamedPipe(@In hNamedPipe: HANDLE, @Direct lpOverlapped: Pointer?): Boolean
    }

    companion object {
        private const val PIPE_ACCESS_DUPLEX = 0x3
        private const val FILE_FLAG_FIRST_PIPE_INSTANCE = 0x80000

        private const val PIPE_TYPE_BYTE = 0x0
        private const val PIPE_REJECT_REMOTE_CLIENTS = 0x8

        private const val ERROR_PIPE_CONNECTED = 0x217
    }
}
