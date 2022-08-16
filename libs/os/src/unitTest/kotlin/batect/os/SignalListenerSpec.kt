/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.os

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import jnr.constants.platform.Signal
import jnr.posix.LibC
import jnr.posix.POSIX
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SignalListenerSpec : Spek({
    describe("a signal listener") {
        val signal = Signal.SIGWINCH
        val libc by createForEachTest { mock<LibC>() }
        val posix by createForEachTest {
            mock<POSIX> {
                on { libc() } doReturn libc
            }
        }

        val signalListener by createForEachTest { SignalListener(posix) }

        on("starting to listen for the signal") {
            beforeEachTest { signalListener.start(signal) {} }

            it("registers a signal handler for the SIGINT signal") {
                verify(libc).signal(eq(signal.value()), any())
            }
        }

        on("the desired signal being received") {
            val signalHandlerCalled by runForEachTest {
                var signalHandlerCalled = false

                signalListener.start(signal) { signalHandlerCalled = true }

                val handlerCaptor = argumentCaptor<LibC.LibCSignalHandler>()
                verify(libc).signal(eq(signal.value()), handlerCaptor.capture())
                handlerCaptor.firstValue.signal(signal.value())

                signalHandlerCalled
            }

            it("calls the handler function") {
                assertThat(signalHandlerCalled, equalTo(true))
            }
        }

        on("stopping listening for the signal") {
            val signalHandlerCalled by runForEachTest {
                var signalHandlerCalled = false

                signalListener.start(signal) { signalHandlerCalled = true }.use { }

                val handlerCaptor = argumentCaptor<LibC.LibCSignalHandler>()
                verify(libc).signal(eq(signal.value()), handlerCaptor.capture())
                handlerCaptor.firstValue.signal(signal.value())

                signalHandlerCalled
            }

            it("no longer calls the signal handler") {
                assertThat(signalHandlerCalled, equalTo(false))
            }
        }

        given("two handlers are registered for the same signal") {
            var firstHandlerCalled = false
            beforeEachTest { signalListener.start(signal) { firstHandlerCalled = true } }

            var secondHandlerCalled = false
            val secondHandlerCleanup by runForEachTest { signalListener.start(signal) { secondHandlerCalled = true } }

            given("the most-recently registered handler has been de-registered") {
                beforeEachTest { secondHandlerCleanup.close() }

                on("the signal being received") {
                    beforeEachTest {
                        val handlerCaptor = argumentCaptor<LibC.LibCSignalHandler>()
                        verify(libc, atLeastOnce()).signal(eq(signal.value()), handlerCaptor.capture())
                        handlerCaptor.firstValue.signal(signal.value())
                    }

                    it("calls the handler function of the remaining handler") {
                        assertThat(firstHandlerCalled, equalTo(true))
                    }

                    it("does not call the handler function of the remaining handler") {
                        assertThat(secondHandlerCalled, equalTo(false))
                    }
                }
            }
        }
    }
})
