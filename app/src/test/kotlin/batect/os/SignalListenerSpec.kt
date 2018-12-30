/*
   Copyright 2017-2018 Charles Korn.

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
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import jnr.constants.platform.Signal
import jnr.posix.POSIX
import jnr.posix.SignalHandler
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object SignalListenerSpec : Spek({
    describe("a signal listener") {
        val signal = Signal.SIGWINCH
        val posix by createForEachTest { mock<POSIX>() }
        val signalListener by createForEachTest { SignalListener(posix) }

        on("starting to listen for the signal") {
            signalListener.start(signal) {}

            it("registers a signal handler for the SIGINT signal") {
                verify(posix).signal(eq(signal), any())
            }
        }

        on("the desired signal being received") {
            val handlerCaptor = argumentCaptor<SignalHandler>()
            var signalHandlerCalled: Boolean = false

            signalListener.start(signal) { signalHandlerCalled = true }

            verify(posix).signal(eq(signal), handlerCaptor.capture())
            handlerCaptor.firstValue.handle(signal.value())

            it("calls the derived class' onSignalReceived() method") {
                assertThat(signalHandlerCalled, equalTo(true))
            }
        }

        on("stopping listening for the signal") {
            val originalHandler = mock<SignalHandler>()
            whenever(posix.signal(eq(signal), any())).doReturn(originalHandler)

            signalListener.start(signal, {}).use { }

            it("restores the previous signal handler") {
                verify(posix).signal(signal, originalHandler)
            }
        }
    }
})
