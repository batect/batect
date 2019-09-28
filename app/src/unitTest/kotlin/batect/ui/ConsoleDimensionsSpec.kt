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

package batect.ui

import batect.os.Dimensions
import batect.os.NativeMethods
import batect.os.NoConsoleException
import batect.os.SignalListener
import batect.os.unix.UnixNativeMethodException
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import jnr.constants.platform.Errno
import jnr.constants.platform.Signal
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ConsoleDimensionsSpec : Spek({
    describe("console dimensions") {
        val nativeMethods by createForEachTest { mock<NativeMethods>() }
        val signalListener by createForEachTest { mock<SignalListener>() }
        val logger by createLoggerForEachTest()

        describe("when created") {
            given("the current console dimensions are available") {
                beforeEachTest { whenever(nativeMethods.getConsoleDimensions()).thenReturn(Dimensions(123, 456)) }

                val dimensions by createForEachTest { ConsoleDimensions(nativeMethods, signalListener, logger) }

                it("registers a listener for the SIGWINCH signal") {
                    verify(signalListener).start(eq(Signal.SIGWINCH), any())
                }

                it("returns the current dimensions when requested") {
                    assertThat(dimensions.current, equalTo(Dimensions(123, 456)))
                }
            }

            given("the current console dimensions are not available because the terminal is not a TTY") {
                beforeEachTest { whenever(nativeMethods.getConsoleDimensions()).thenThrow(NoConsoleException()) }

                val dimensions by createForEachTest { ConsoleDimensions(nativeMethods, signalListener, logger) }

                it("registers a listener for the SIGWINCH signal") {
                    verify(signalListener).start(eq(Signal.SIGWINCH), any())
                }

                it("returns null when the current dimensions are requested") {
                    assertThat(dimensions.current, absent())
                }
            }

            given("the current console dimensions are not available for another reason") {
                val exception = UnixNativeMethodException("ioctl", Errno.EEXIST)

                beforeEachTest { whenever(nativeMethods.getConsoleDimensions()).thenThrow(exception) }

                val dimensions by createForEachTest { ConsoleDimensions(nativeMethods, signalListener, logger) }

                it("registers a listener for the SIGWINCH signal") {
                    verify(signalListener).start(eq(Signal.SIGWINCH), any())
                }

                it("throws an exception when the current dimensions are requested") {
                    assertThat({ dimensions.current }, throws<Throwable>(equalTo(exception)))
                }
            }
        }

        describe("when the console dimensions have not changed") {
            beforeEachTest { whenever(nativeMethods.getConsoleDimensions()).thenReturn(Dimensions(123, 456)) }

            val dimensions by createForEachTest { ConsoleDimensions(nativeMethods, signalListener, logger) }

            beforeEachTest { repeat(10) { dimensions.current } }

            it("does not query the operating system multiple times") {
                verify(nativeMethods, times(1)).getConsoleDimensions()
            }
        }

        describe("when the console dimensions change") {
            beforeEachTest { whenever(nativeMethods.getConsoleDimensions()).thenReturn(Dimensions(1, 1)) }

            val dimensions by createForEachTest { ConsoleDimensions(nativeMethods, signalListener, logger) }

            val signalHandler by runForEachTest {
                val handlerCaptor = argumentCaptor<() -> Unit>()
                verify(signalListener).start(eq(Signal.SIGWINCH), handlerCaptor.capture())
                handlerCaptor.firstValue
            }

            given("the current console dimensions are available") {
                var notifiedListener = false

                beforeEachTest {
                    whenever(nativeMethods.getConsoleDimensions()).thenReturn(Dimensions(123, 456))

                    dimensions.registerListener { notifiedListener = true }

                    signalHandler()
                }

                it("returns the updated dimensions when requested") {
                    assertThat(dimensions.current, equalTo(Dimensions(123, 456)))
                }

                it("notifies any registered listeners that the dimensions have changed") {
                    assertThat(notifiedListener, equalTo(true))
                }
            }

            given("the current console dimensions are not available because the terminal is not a TTY") {
                var notifiedListener = false

                beforeEachTest {
                    whenever(nativeMethods.getConsoleDimensions()).thenThrow(NoConsoleException())

                    dimensions.registerListener { notifiedListener = true }

                    signalHandler()
                }

                it("returns null when the current dimensions are requested") {
                    assertThat(dimensions.current, absent())
                }

                it("notifies any registered listeners that the dimensions have changed") {
                    assertThat(notifiedListener, equalTo(true))
                }
            }

            given("the current console dimensions are not available for another reason") {
                val exception = UnixNativeMethodException("ioctl", Errno.EEXIST)

                beforeEachTest {
                    whenever(nativeMethods.getConsoleDimensions()).thenThrow(exception)

                    signalHandler()
                }

                it("throws an exception when the current dimensions are requested") {
                    assertThat({ dimensions.current }, throws<Throwable>(equalTo(exception)))
                }
            }
        }

        describe("after removing a change listener") {
            beforeEachTest { whenever(nativeMethods.getConsoleDimensions()).thenReturn(Dimensions(123, 456)) }

            val dimensions by createForEachTest { ConsoleDimensions(nativeMethods, signalListener, logger) }

            val signalHandler by runForEachTest {
                val handlerCaptor = argumentCaptor<() -> Unit>()
                verify(signalListener).start(eq(Signal.SIGWINCH), handlerCaptor.capture())
                handlerCaptor.firstValue
            }

            var notifiedListener = false

            beforeEachTest {
                val cleanup = dimensions.registerListener { notifiedListener = true }
                cleanup.close()

                signalHandler()
            }

            it("does not receive a notification when the console dimensions change") {
                assertThat(notifiedListener, equalTo(false))
            }
        }
    }
})
