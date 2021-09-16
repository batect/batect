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

package batect.os.windows

import batect.os.Dimensions
import batect.os.NativeMethodException
import batect.os.NoConsoleException
import batect.os.WindowsNativeMethodException
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withMessage
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import jnr.constants.platform.windows.LastError
import jnr.ffi.Pointer
import jnr.ffi.Runtime
import jnr.ffi.byref.IntByReference
import jnr.posix.HANDLE
import jnr.posix.POSIX
import jnr.posix.WindowsLibC
import jnr.posix.util.WindowsHelpers
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.FileDescriptor
import java.nio.ByteBuffer

object WindowsNativeMethodsSpec : Spek({
    describe("Windows native methods") {
        val win32 by createForEachTest { mock<WindowsNativeMethods.Win32>() }
        val runtime = Runtime.getSystemRuntime()
        val posix by createForEachTest { mock<POSIX>() }
        val nativeMethods by createForEachTest { WindowsNativeMethods(posix, win32, runtime) }

        describe("getting the console dimensions") {
            given("getting the handle for stdout succeeds") {
                val stdoutHandle = mock<HANDLE> {
                    on { isValid } doReturn true
                }

                beforeEachTest { whenever(win32.GetStdHandle(WindowsLibC.STD_OUTPUT_HANDLE)).thenReturn(stdoutHandle) }

                on("the call to GetConsoleScreenBufferInfo succeeding") {
                    beforeEachTest {
                        whenever(win32.GetConsoleScreenBufferInfo(eq(stdoutHandle), any())).thenAnswer { invocation ->
                            val info = invocation.arguments[1] as WindowsNativeMethods.ConsoleScreenBufferInfo

                            info.srWindow.top.set(0)
                            info.srWindow.bottom.set(100)
                            info.srWindow.left.set(10)
                            info.srWindow.right.set(20)

                            true
                        }
                    }

                    val dimensions by runForEachTest { nativeMethods.getConsoleDimensions() }

                    it("returns a set of dimensions with the expected values") {
                        assertThat(dimensions, equalTo(Dimensions(101, 11)))
                    }
                }

                on("the call to GetConsoleScreenBufferInfo failing") {
                    beforeEachTest {
                        whenever(win32.GetConsoleScreenBufferInfo(eq(stdoutHandle), any())).thenReturn(false)
                    }

                    given("it failed because stdout is not connected to a console") {
                        beforeEachTest {
                            whenever(posix.errno()).thenReturn(LastError.ERROR_INVALID_HANDLE.value())
                        }

                        it("throws an appropriate exception") {
                            assertThat({ nativeMethods.getConsoleDimensions() }, throws<NoConsoleException>())
                        }
                    }

                    given("it failed for another reason") {
                        beforeEachTest {
                            whenever(posix.errno()).thenReturn(LastError.ERROR_FILE_NOT_FOUND.value())
                        }

                        it("throws an appropriate exception") {
                            assertThat(
                                { nativeMethods.getConsoleDimensions() },
                                throws<WindowsNativeMethodException>(withMethod("GetConsoleScreenBufferInfo") and withError(LastError.ERROR_FILE_NOT_FOUND))
                            )
                        }
                    }
                }
            }

            given("getting the handle for stdout fails") {
                val stdoutHandle = mock<HANDLE> {
                    on { isValid } doReturn false
                }

                beforeEachTest {
                    whenever(win32.GetStdHandle(WindowsLibC.STD_OUTPUT_HANDLE)).thenReturn(stdoutHandle)
                    whenever(posix.errno()).doReturn(LastError.ERROR_FILE_NOT_FOUND.intValue())
                }

                it("throws an appropriate exception") {
                    assertThat({ nativeMethods.getConsoleDimensions() }, throws(withMethod("GetStdHandle") and withError(LastError.ERROR_FILE_NOT_FOUND)))
                }
            }
        }

        describe("determining if stdin is a TTY") {
            given("stdin is not a character device") {
                beforeEachTest { whenever(posix.isatty(FileDescriptor.`in`)).doReturn(false) }

                it("returns that stdin is not a TTY") {
                    assertThat(nativeMethods.determineIfStdinIsTTY(), equalTo(false))
                }
            }

            given("stdin is a character device") {
                val stdinHandle = mock<HANDLE> {
                    on { isValid } doReturn true
                }

                beforeEachTest {
                    whenever(posix.isatty(FileDescriptor.`in`)).doReturn(true)
                    whenever(win32.GetStdHandle(WindowsLibC.STD_INPUT_HANDLE)).doReturn(stdinHandle)
                }

                given("stdin is a console device") {
                    beforeEachTest { whenever(win32.GetConsoleMode(eq(stdinHandle), any())).doReturn(true) }

                    it("returns that stdin is a TTY") {
                        assertThat(nativeMethods.determineIfStdinIsTTY(), equalTo(true))
                    }
                }

                given("stdin is not a console device") {
                    beforeEachTest {
                        whenever(win32.GetConsoleMode(eq(stdinHandle), any())).doReturn(false)
                        whenever(posix.errno()).doReturn(LastError.ERROR_INVALID_HANDLE.intValue())
                    }

                    it("returns that stdin is not a TTY") {
                        assertThat(nativeMethods.determineIfStdinIsTTY(), equalTo(false))
                    }
                }
            }
        }

        describe("determining if stdout is a TTY") {
            given("stdout is not a character device") {
                beforeEachTest { whenever(posix.isatty(FileDescriptor.out)).doReturn(false) }

                it("returns that stdout is not a TTY") {
                    assertThat(nativeMethods.determineIfStdoutIsTTY(), equalTo(false))
                }
            }

            given("stdout is a character device") {
                val stdoutHandle = mock<HANDLE> {
                    on { isValid } doReturn true
                }

                beforeEachTest {
                    whenever(posix.isatty(FileDescriptor.out)).doReturn(true)
                    whenever(win32.GetStdHandle(WindowsLibC.STD_OUTPUT_HANDLE)).doReturn(stdoutHandle)
                }

                given("stdout is a console device") {
                    beforeEachTest { whenever(win32.GetConsoleMode(eq(stdoutHandle), any())).doReturn(true) }

                    it("returns that stdout is a TTY") {
                        assertThat(nativeMethods.determineIfStdoutIsTTY(), equalTo(true))
                    }
                }

                given("stdout is not a console device") {
                    beforeEachTest {
                        whenever(win32.GetConsoleMode(eq(stdoutHandle), any())).doReturn(false)
                        whenever(posix.errno()).doReturn(LastError.ERROR_INVALID_HANDLE.intValue())
                    }

                    it("returns that stdout is not a TTY") {
                        assertThat(nativeMethods.determineIfStdoutIsTTY(), equalTo(false))
                    }
                }
            }
        }

        describe("determining if stderr is a TTY") {
            given("stderr is not a character device") {
                beforeEachTest { whenever(posix.isatty(FileDescriptor.err)).doReturn(false) }

                it("returns that stderr is not a TTY") {
                    assertThat(nativeMethods.determineIfStderrIsTTY(), equalTo(false))
                }
            }

            given("stderr is a character device") {
                val stderrHandle = mock<HANDLE> {
                    on { isValid } doReturn true
                }

                beforeEachTest {
                    whenever(posix.isatty(FileDescriptor.err)).doReturn(true)
                    whenever(win32.GetStdHandle(WindowsLibC.STD_ERROR_HANDLE)).doReturn(stderrHandle)
                }

                given("stderr is a console device") {
                    beforeEachTest { whenever(win32.GetConsoleMode(eq(stderrHandle), any())).doReturn(true) }

                    it("returns that stderr is a TTY") {
                        assertThat(nativeMethods.determineIfStderrIsTTY(), equalTo(true))
                    }
                }

                given("stderr is not a console device") {
                    beforeEachTest {
                        whenever(win32.GetConsoleMode(eq(stderrHandle), any())).doReturn(false)
                        whenever(posix.errno()).doReturn(LastError.ERROR_INVALID_HANDLE.intValue())
                    }

                    it("returns that stderr is not a TTY") {
                        assertThat(nativeMethods.determineIfStderrIsTTY(), equalTo(false))
                    }
                }
            }
        }

        describe("enabling console raw mode") {
            given("getting the handle for stdin succeeds") {
                val stdinHandle = mock<HANDLE> {
                    on { isValid } doReturn true
                }

                beforeEachTest { whenever(win32.GetStdHandle(WindowsLibC.STD_INPUT_HANDLE)).thenReturn(stdinHandle) }

                given("getting the current state succeeds") {
                    // ECHO_INPUT + LINE_INPUT + MOUSE_INPUT + WINDOW_INPUT + PROCESSED_INPUT + 0x100 (which should remain set)
                    val existingConsoleMode = 0x0000011F

                    beforeEachTest {
                        whenever(win32.GetConsoleMode(any(), any())).thenAnswer { invocation ->
                            val mode = invocation.arguments[1] as IntByReference
                            mode.set(existingConsoleMode, runtime)

                            true
                        }
                    }

                    given("setting the new state succeeds") {
                        beforeEachTest {
                            whenever(win32.SetConsoleMode(any(), any())).thenReturn(true)
                        }

                        val stateToRestore by createForEachTest { nativeMethods.enableConsoleRawMode() }

                        it("returns the existing state so that it can be restored later") {
                            assertThat(stateToRestore, equalTo(existingConsoleMode))
                        }

                        it("enabled the desired console modes and disables the unwanted ones, preserving any other settings") {
                            // EXTENDED_FLAGS + INSERT_MODE + QUICK_EDIT_MODE + ENABLE_VIRTUAL_TERMINAL_OUTPUT + 0x100 (which should remain set)
                            verify(win32).SetConsoleMode(stdinHandle, 0x3E0)
                        }
                    }

                    given("setting the new state fails") {
                        beforeEachTest {
                            whenever(win32.SetConsoleMode(any(), any())).thenReturn(false)
                            whenever(posix.errno()).doReturn(LastError.ERROR_FILE_NOT_FOUND.intValue())
                        }

                        it("throws an appropriate exception") {
                            assertThat({ nativeMethods.enableConsoleRawMode() }, throws(withMethod("SetConsoleMode") and withError(LastError.ERROR_FILE_NOT_FOUND)))
                        }
                    }
                }

                given("getting the current state fails") {
                    beforeEachTest {
                        whenever(win32.GetConsoleMode(any(), any())).thenReturn(false)
                        whenever(posix.errno()).doReturn(LastError.ERROR_FILE_NOT_FOUND.intValue())
                    }

                    it("throws an appropriate exception") {
                        assertThat({ nativeMethods.enableConsoleRawMode() }, throws(withMethod("GetConsoleMode") and withError(LastError.ERROR_FILE_NOT_FOUND)))
                    }
                }
            }

            given("getting the handle for stdin fails") {
                val stdinHandle = mock<HANDLE> {
                    on { isValid } doReturn false
                }

                beforeEachTest {
                    whenever(win32.GetStdHandle(WindowsLibC.STD_INPUT_HANDLE)).thenReturn(stdinHandle)
                    whenever(posix.errno()).doReturn(LastError.ERROR_FILE_NOT_FOUND.intValue())
                }

                it("throws an appropriate exception") {
                    assertThat({ nativeMethods.enableConsoleRawMode() }, throws(withMethod("GetStdHandle") and withError(LastError.ERROR_FILE_NOT_FOUND)))
                }
            }
        }

        describe("restoring the console mode") {
            given("getting the handle for stdin succeeds") {
                val stdinHandle = mock<HANDLE> {
                    on { isValid } doReturn true
                }

                beforeEachTest { whenever(win32.GetStdHandle(WindowsLibC.STD_INPUT_HANDLE)).thenReturn(stdinHandle) }

                given("setting the new state succeeds") {
                    beforeEachTest {
                        whenever(win32.SetConsoleMode(any(), any())).thenReturn(true)

                        nativeMethods.restoreConsoleMode(123)
                    }

                    it("sets the console state to the provided previous value") {
                        verify(win32).SetConsoleMode(stdinHandle, 123)
                    }
                }

                given("setting the new state fails") {
                    beforeEachTest {
                        whenever(win32.SetConsoleMode(any(), any())).thenReturn(false)
                        whenever(posix.errno()).doReturn(LastError.ERROR_FILE_NOT_FOUND.intValue())
                    }

                    it("throws an appropriate exception") {
                        assertThat({ nativeMethods.restoreConsoleMode(123) }, throws(withMethod("SetConsoleMode") and withError(LastError.ERROR_FILE_NOT_FOUND)))
                    }
                }
            }

            given("getting the handle for stdin fails") {
                val stdinHandle = mock<HANDLE> {
                    on { isValid } doReturn false
                }

                beforeEachTest {
                    whenever(win32.GetStdHandle(WindowsLibC.STD_INPUT_HANDLE)).thenReturn(stdinHandle)
                    whenever(posix.errno()).doReturn(LastError.ERROR_FILE_NOT_FOUND.intValue())
                }

                it("throws an appropriate exception") {
                    assertThat({ nativeMethods.restoreConsoleMode(123) }, throws(withMethod("GetStdHandle") and withError(LastError.ERROR_FILE_NOT_FOUND)))
                }
            }
        }

        describe("getting the current user and group information") {
            it("returns a value indicating that getting the user ID is not supported on Windows") {
                assertThat({ nativeMethods.getUserId() }, throws<UnsupportedOperationException>(withMessage("Getting the user ID is not supported on Windows.")))
            }

            it("returns a value indicating that getting the group ID is not supported on Windows") {
                assertThat({ nativeMethods.getGroupId() }, throws<UnsupportedOperationException>(withMessage("Getting the group ID is not supported on Windows.")))
            }

            it("returns a value indicating that getting the group name is not supported on Windows") {
                assertThat({ nativeMethods.getGroupName() }, throws<UnsupportedOperationException>(withMessage("Getting the group name is not supported on Windows.")))
            }

            given("getting the current user name succeeds") {
                beforeEachTest {
                    whenever(win32.GetUserNameW(any(), any())).doAnswer { invocation ->
                        val buffer = invocation.arguments[0] as ByteBuffer
                        val length = invocation.arguments[1] as IntByReference
                        val bytes = WindowsHelpers.toWString("awesome-user")

                        buffer.put(bytes)
                        length.set(bytes.size / 2, runtime)

                        true
                    }
                }

                it("returns the user name reported by the system API") {
                    assertThat(nativeMethods.getUserName(), equalTo("awesome-user"))
                }
            }

            given("getting the current user name fails") {
                beforeEachTest {
                    whenever(win32.GetUserNameW(any(), any())).doReturn(false)
                    whenever(posix.errno()).doReturn(LastError.ERROR_FILE_NOT_FOUND.intValue())
                }

                it("throws an appropriate exception") {
                    assertThat({ nativeMethods.getUserName() }, throws(withMethod("GetUserNameW") and withError(LastError.ERROR_FILE_NOT_FOUND)))
                }
            }
        }

        describe("enabling console escape sequences") {
            given("getting the handle for stdout succeeds") {
                val stdoutHandle = mock<HANDLE> {
                    on { isValid } doReturn true
                }

                beforeEachTest { whenever(win32.GetStdHandle(WindowsLibC.STD_OUTPUT_HANDLE)).thenReturn(stdoutHandle) }

                given("getting the current console mode succeeds") {
                    val existingConsoleMode = 0x00000003

                    beforeEachTest {
                        whenever(win32.GetConsoleMode(any(), any())).thenAnswer { invocation ->
                            val mode = invocation.arguments[1] as IntByReference
                            mode.set(existingConsoleMode, runtime)

                            true
                        }
                    }

                    given("setting the current console mode succeeds") {
                        beforeEachTest { whenever(win32.SetConsoleMode(any(), any())).thenReturn(true) }
                        beforeEachTest { nativeMethods.enableConsoleEscapeSequences() }

                        it("enables virtual terminal processing and new line auto return") {
                            verify(win32).SetConsoleMode(stdoutHandle, existingConsoleMode or 0x4 or 0x8)
                        }
                    }

                    given("setting the current console mode fails") {
                        beforeEachTest {
                            whenever(win32.SetConsoleMode(any(), any())).thenReturn(false)
                            whenever(posix.errno()).doReturn(LastError.ERROR_FILE_NOT_FOUND.intValue())
                        }

                        it("throws an appropriate exception") {
                            assertThat({ nativeMethods.enableConsoleEscapeSequences() }, throws(withMethod("SetConsoleMode") and withError(LastError.ERROR_FILE_NOT_FOUND)))
                        }
                    }
                }

                given("getting the current console mode fails") {
                    beforeEachTest {
                        whenever(win32.GetConsoleMode(any(), any())).thenReturn(false)
                        whenever(posix.errno()).doReturn(LastError.ERROR_FILE_NOT_FOUND.intValue())
                    }

                    it("throws an appropriate exception") {
                        assertThat({ nativeMethods.enableConsoleEscapeSequences() }, throws(withMethod("GetConsoleMode") and withError(LastError.ERROR_FILE_NOT_FOUND)))
                    }
                }
            }

            given("getting the handle for stdout fails") {
                val stdoutHandle = mock<HANDLE> {
                    on { isValid } doReturn false
                }

                beforeEachTest {
                    whenever(win32.GetStdHandle(WindowsLibC.STD_OUTPUT_HANDLE)).thenReturn(stdoutHandle)
                    whenever(posix.errno()).doReturn(LastError.ERROR_FILE_NOT_FOUND.intValue())
                }

                it("throws an appropriate exception") {
                    assertThat({ nativeMethods.enableConsoleEscapeSequences() }, throws(withMethod("GetStdHandle") and withError(LastError.ERROR_FILE_NOT_FOUND)))
                }
            }
        }
    }
})

private fun withMethod(method: String): Matcher<NativeMethodException> {
    return has(NativeMethodException::method, equalTo(method))
}

private fun withError(error: LastError): Matcher<WindowsNativeMethodException> {
    return has(WindowsNativeMethodException::error, equalTo(error))
}

private fun IntByReference.set(value: Int, runtime: Runtime) {
    val byte1 = (value ushr 24).toByte()
    val byte2 = ((value ushr 16) and 0xFF).toByte()
    val byte3 = ((value ushr 8) and 0xFF).toByte()
    val byte4 = (value and 0xFF).toByte()
    val memory = ByteBuffer.wrap(byteArrayOf(byte1, byte2, byte3, byte4))

    this.fromNative(runtime, Pointer.wrap(runtime, memory), 0)
}
