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

package batect.execution

import batect.execution.model.events.TaskEventSink
import batect.execution.model.events.UserInterruptedExecutionEvent
import batect.testutils.createForEachTest
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

object InterruptionTrapSpec : Spek({
    describe("an interruption trap") {
        val posix by createForEachTest { mock<POSIX>() }
        val trap by createForEachTest { InterruptionTrap(posix) }

        describe("stopping execution when a SIGINT is received") {
            val eventSink by createForEachTest { mock<TaskEventSink>() }

            on("starting monitoring") {
                trap.trapInterruptions(eventSink)

                it("registers a signal handler for the SIGINT signal") {
                    verify(posix).signal(eq(Signal.SIGINT), any())
                }
            }

            on("a SIGINT being received") {
                val handlerCaptor = argumentCaptor<SignalHandler>()

                trap.trapInterruptions(eventSink)

                verify(posix).signal(eq(Signal.SIGINT), handlerCaptor.capture())
                handlerCaptor.firstValue.handle(Signal.SIGINT.value())

                it("sends a 'user interrupted execution' event to the event sink") {
                    verify(eventSink).postEvent(UserInterruptedExecutionEvent)
                }
            }

            on("stopping monitoring") {
                val originalHandler = mock<SignalHandler>()
                whenever(posix.signal(eq(Signal.SIGINT), any())).doReturn(originalHandler)

                trap.trapInterruptions(eventSink).use { }

                it("restores the previous SIGINT signal handler") {
                    verify(posix).signal(Signal.SIGINT, originalHandler)
                }
            }
        }
    }
})
