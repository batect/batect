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

package batect.execution

import batect.execution.model.events.TaskEventSink
import batect.execution.model.events.UserInterruptedExecutionEvent
import batect.os.SignalListener
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.on
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import jnr.constants.platform.Signal
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object InterruptionTrapSpec : Spek({
    describe("an interruption trap") {
        val listener by createForEachTest { mock<SignalListener>() }
        val trap by createForEachTest { InterruptionTrap(listener) }

        describe("stopping execution when a SIGINT is received") {
            val eventSink by createForEachTest { mock<TaskEventSink>() }

            on("starting monitoring") {
                val cleanup = mock<AutoCloseable>()

                beforeEachTest { whenever(listener.start(any(), any())).doReturn(cleanup) }

                val returnedCleanup by runForEachTest { trap.trapInterruptions(eventSink) }

                it("registers a signal handler for the SIGINT signal") {
                    verify(listener).start(eq(Signal.SIGINT), any())
                }

                it("returns the cleanup handler from the signal listener") {
                    assertThat(returnedCleanup, equalTo(cleanup))
                }
            }

            on("a SIGINT being received") {
                beforeEachTest {
                    val handlerCaptor = argumentCaptor<() -> Unit>()

                    trap.trapInterruptions(eventSink)

                    verify(listener).start(eq(Signal.SIGINT), handlerCaptor.capture())
                    handlerCaptor.firstValue.invoke()
                }

                it("sends a 'user interrupted execution' event to the event sink") {
                    verify(eventSink).postEvent(UserInterruptedExecutionEvent)
                }
            }
        }
    }
})
