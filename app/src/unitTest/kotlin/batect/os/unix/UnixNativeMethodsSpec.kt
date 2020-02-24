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
import batect.os.NativeMethodException
import batect.os.NoConsoleException
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
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import jnr.constants.platform.Errno
import jnr.ffi.Platform
import jnr.ffi.Runtime
import jnr.posix.Group
import jnr.posix.POSIX
import jnr.posix.Passwd
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object UnixNativeMethodsSpec : Spek({
    describe("Unix native methods") {
        val libc by createForEachTest { mock<UnixNativeMethods.LibC>() }
        val runtime = Runtime.getSystemRuntime()
        val platform by createForEachTest { mock<Platform>() }
        val posix by createForEachTest { mock<POSIX>() }
        val nativeMethods by createForEachTest { UnixNativeMethods(libc, runtime, platform, posix) }

        describe("getting the console dimensions") {
            describe("when running on any supported platform") {
                beforeEachTest { whenever(platform.os).thenReturn(Platform.OS.DARWIN) }

                on("calling ioctl() succeeding") {
                    val expectedRows = 123
                    val expectedColumns = 456

                    beforeEachTest {
                        whenever(libc.ioctl(eq(0), any(), any())).thenAnswer { invocation ->
                            val size = invocation.arguments[2] as UnixNativeMethods.WindowSize

                            size.ws_row.set(expectedRows)
                            size.ws_col.set(expectedColumns)

                            0
                        }
                    }

                    val dimensions by runForEachTest { nativeMethods.getConsoleDimensions() }

                    it("returns a set of dimensions with the expected values") {
                        assertThat(dimensions, equalTo(Dimensions(expectedRows, expectedColumns)))
                    }
                }

                on("calling ioctl() failing") {
                    beforeEachTest {
                        whenever(libc.ioctl(any(), any(), any())).thenReturn(-1)
                    }

                    given("it failed because stdin is not connected to a TTY") {
                        beforeEachTest {
                            whenever(posix.errno()).thenReturn(Errno.ENOTTY.intValue())
                        }

                        it("throws an appropriate exception") {
                            assertThat({ nativeMethods.getConsoleDimensions() }, throws<NoConsoleException>())
                        }
                    }

                    given("it failed because stdin is redirected") {
                        beforeEachTest {
                            whenever(posix.errno()).thenReturn(Errno.ENODEV.intValue())
                        }

                        it("throws an appropriate exception") {
                            assertThat({ nativeMethods.getConsoleDimensions() }, throws<NoConsoleException>())
                        }
                    }

                    given("it failed for another reason") {
                        beforeEachTest {
                            whenever(posix.errno()).thenReturn(Errno.ENOENT.intValue())
                        }

                        it("throws an appropriate exception") {
                            assertThat({ nativeMethods.getConsoleDimensions() }, throws<UnixNativeMethodException>(withMethod("ioctl") and withError(Errno.ENOENT)))
                        }
                    }
                }
            }

            describe("when running on macOS") {
                beforeEachTest { whenever(platform.os).thenReturn(Platform.OS.DARWIN) }

                on("calling ioctl()") {
                    beforeEachTest { nativeMethods.getConsoleDimensions() }

                    it("invokes ioctl() with the macOS-specific value for TIOCGWINSZ") {
                        verify(libc).ioctl(any(), eq(0x40087468), any())
                    }
                }
            }

            describe("when running on Linux") {
                beforeEachTest { whenever(platform.os).thenReturn(Platform.OS.LINUX) }

                on("calling ioctl()") {
                    beforeEachTest { nativeMethods.getConsoleDimensions() }

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

        describe("getting the current user and group information") {
            val userInfo = mock<Passwd> {
                on { loginName } doReturn "awesome-user"
            }

            val groupInfo = mock<Group> {
                on { name } doReturn "awesome-group"
            }

            beforeEachTest {
                whenever(posix.geteuid()).doReturn(123)
                whenever(posix.getpwuid(123)).doReturn(userInfo)
                whenever(posix.getegid()).doReturn(456)
                whenever(posix.getgrgid(456)).doReturn(groupInfo)
            }

            it("returns the user ID reported by the system API") {
                assertThat(nativeMethods.getUserId(), equalTo(123))
            }

            it("returns the user name reported by the system API") {
                assertThat(nativeMethods.getUserName(), equalTo("awesome-user"))
            }

            it("returns the group ID reported by the system API") {
                assertThat(nativeMethods.getGroupId(), equalTo(456))
            }

            it("returns the group name reported by the system API") {
                assertThat(nativeMethods.getGroupName(), equalTo("awesome-group"))
            }
        }
    }
})

fun withMethod(method: String): Matcher<NativeMethodException> {
    return has(NativeMethodException::method, equalTo(method))
}

fun withError(error: Errno): Matcher<UnixNativeMethodException> {
    return has(UnixNativeMethodException::error, equalTo(error))
}
