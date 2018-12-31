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

package batect.os

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.withMessage
import batect.ui.Dimensions
import com.natpryce.hamkrest.Matcher
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.has
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import jnr.constants.platform.Errno
import jnr.ffi.Platform
import jnr.ffi.Runtime
import jnr.posix.POSIX
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object NativeMethodsSpec : Spek({
    describe("native methods") {
        val libc by createForEachTest { mock<NativeMethods.LibC>() }
        val runtime = Runtime.getSystemRuntime()
        val platform by createForEachTest { mock<Platform>() }
        val posix by createForEachTest { mock<POSIX>() }
        val nativeMethods by createForEachTest { NativeMethods(libc, runtime, platform, posix) }

        describe("getting the console dimensions") {
            describe("when running on any supported platform") {
                beforeEachTest { whenever(platform.os).thenReturn(Platform.OS.DARWIN) }

                on("calling ioctl() succeeding") {
                    val expectedRows = 123
                    val expectedColumns = 456

                    whenever(libc.ioctl(eq(0), any(), any())).thenAnswer { invocation ->
                        val size = invocation.arguments[2] as NativeMethods.WindowSize

                        size.ws_row.set(expectedRows)
                        size.ws_col.set(expectedColumns)

                        0
                    }

                    val dimensions = nativeMethods.getConsoleDimensions()

                    it("returns a set of dimensions with the expected values") {
                        assertThat(dimensions, equalTo(Dimensions(expectedRows, expectedColumns)))
                    }
                }

                on("calling ioctl() failing") {
                    whenever(libc.ioctl(any(), any(), any())).thenReturn(-1)
                    whenever(posix.errno()).thenReturn(Errno.ENOTTY.intValue())

                    it("throws an appropriate exception") {
                        assertThat({ nativeMethods.getConsoleDimensions() }, throws<NativeMethodException>(withMethod("ioctl") and withError(Errno.ENOTTY)))
                    }
                }
            }

            describe("when running on OS X") {
                beforeEachTest { whenever(platform.os).thenReturn(Platform.OS.DARWIN) }

                on("calling ioctl()") {
                    nativeMethods.getConsoleDimensions()

                    it("invokes ioctl() with the OS X-specific value for TIOCGWINSZ") {
                        verify(libc).ioctl(any(), eq(0x40087468), any())
                    }
                }
            }

            describe("when running on Linux") {
                beforeEachTest { whenever(platform.os).thenReturn(Platform.OS.LINUX) }

                on("calling ioctl()") {
                    nativeMethods.getConsoleDimensions()

                    it("invokes ioctl() with the Linux-specific value for TIOCGWINSZ") {
                        verify(libc).ioctl(any(), eq(0x00005413), any())
                    }
                }
            }

            describe("when running on unsupported operating system") {
                beforeEachTest { whenever(platform.os).thenReturn(Platform.OS.WINDOWS) }

                on("getting the console dimensions") {
                    it("throws an appropriate exception") {
                        assertThat({ nativeMethods.getConsoleDimensions() }, throws<UnsupportedOperationException>(withMessage("The platform WINDOWS is not supported.")))
                    }
                }
            }
        }
    }
})

fun withMethod(method: String): Matcher<NativeMethodException> {
    return has(NativeMethodException::method, equalTo(method))
}

fun withError(error: Errno): Matcher<NativeMethodException> {
    return has(NativeMethodException::error, equalTo(error))
}
