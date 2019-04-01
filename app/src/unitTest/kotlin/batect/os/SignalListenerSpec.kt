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
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import jnr.constants.platform.Signal
import jnr.posix.POSIX
import jnr.posix.SignalHandler
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SignalListenerSpec : Spek({
    describe("a signal listener") {
        val signal = Signal.SIGWINCH
        val posix by createForEachTest { mock<POSIX>() }
        val signalListener by createForEachTest { SignalListener(posix) }

        on("starting to listen for the signal") {
            beforeEachTest { signalListener.start(signal) {} }

            it("registers a signal handler for the SIGINT signal") {
                verify(posix).signal(eq(signal), any())
            }
        }

        on("the desired signal being received") {
            val signalHandlerCalled by runForEachTest {
                var signalHandlerCalled = false

                signalListener.start(signal) { signalHandlerCalled = true }

                val handlerCaptor = argumentCaptor<SignalHandler>()
                verify(posix).signal(eq(signal), handlerCaptor.capture())
                handlerCaptor.firstValue.handle(signal.value())

                signalHandlerCalled
            }

            it("calls the handler function") {
                assertThat(signalHandlerCalled, equalTo(true))
            }
        }

        on("stopping listening for the signal") {
            val originalHandler by createForEachTest { mock<SignalHandler>() }

            beforeEachTest {
                whenever(posix.signal(eq(signal), any())).doReturn(originalHandler)

                signalListener.start(signal, {}).use { }
            }

            it("restores the previous signal handler") {
                verify(posix).signal(signal, originalHandler)
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
                        val handlerCaptor = argumentCaptor<SignalHandler>()
                        verify(posix, atLeastOnce()).signal(eq(signal), handlerCaptor.capture())
                        handlerCaptor.firstValue.handle(signal.value())
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
