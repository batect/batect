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
import batect.os.NoConsoleException
import batect.os.PossiblyUnsupportedValue
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.ui.Dimensions
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import jnr.constants.platform.windows.LastError
import jnr.ffi.Pointer
import jnr.ffi.Runtime
import jnr.ffi.byref.IntByReference
import jnr.posix.HANDLE
import jnr.posix.POSIX
import jnr.posix.WindowsLibC
import jnr.posix.util.WindowsHelpers
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.ByteBuffer

object WindowsNativeMethodsSpec : Spek({
    describe("Windows native methods") {
        val win32 by createForEachTest { mock<WindowsNativeMethods.Win32>() }
        val runtime = Runtime.getSystemRuntime()
        val posix by createForEachTest { mock<POSIX>() }
        val nativeMethods by createForEachTest { WindowsNativeMethods(posix, win32, runtime) }

        describe("getting the console dimensions") {
            val stdoutHandle = mock<HANDLE>()

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

        describe("getting the current user and group information") {
            it("returns a value indicating that getting the user ID is not supported on Windows") {
                assertThat(nativeMethods.getUserId(), equalTo(PossiblyUnsupportedValue.Unsupported("Getting the user ID is not supported on Windows.")))
            }

            it("returns a value indicating that getting the group ID is not supported on Windows") {
                assertThat(nativeMethods.getGroupId(), equalTo(PossiblyUnsupportedValue.Unsupported("Getting the group ID is not supported on Windows.")))
            }

            it("returns a value indicating that getting the group name is not supported on Windows") {
                assertThat(nativeMethods.getGroupName(), equalTo(PossiblyUnsupportedValue.Unsupported("Getting the group name is not supported on Windows.")))
            }

            given("getting the current user name succeeds") {
                beforeEachTest {
                    whenever(win32.GetUserNameW(any(), any())).doAnswer { invocation ->
                        val buffer = invocation.arguments[0] as ByteBuffer
                        val length = invocation.arguments[1] as IntByReference
                        val bytes = WindowsHelpers.toWString("awesome-user")

                        buffer.put(bytes)
                        length.set((bytes.size / 2).toByte(), runtime)

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
                    assertThat({ nativeMethods.getUserName() }, throws<WindowsNativeMethodException>(withMethod("GetUserNameW") and withError(LastError.ERROR_FILE_NOT_FOUND)))
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

private fun IntByReference.set(value: Byte, runtime: Runtime) {
    val memory = ByteBuffer.wrap(byteArrayOf(0, 0, 0, value))

    this.fromNative(runtime, Pointer.wrap(runtime, memory), 0)
}
