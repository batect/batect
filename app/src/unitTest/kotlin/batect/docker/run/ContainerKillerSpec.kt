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

package batect.docker.run

import batect.docker.DockerAPI
import batect.docker.DockerContainer
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

object ContainerKillerSpec : Spek({
    describe("a container killer") {
        val api by createForEachTest { mock<DockerAPI>() }
        val listener by createForEachTest { mock<SignalListener>() }
        val killer by createForEachTest { ContainerKiller(api, listener) }

        describe("killing a container when a SIGINT is received") {
            val container = DockerContainer("the-container")

            on("starting monitoring") {
                val cleanup = mock<AutoCloseable>()

                beforeEachTest { whenever(listener.start(any(), any())).doReturn(cleanup) }

                val returnedCleanup by runForEachTest { killer.killContainerOnSigint(container) }

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

                    killer.killContainerOnSigint(container)

                    verify(listener).start(eq(Signal.SIGINT), handlerCaptor.capture())
                    handlerCaptor.firstValue.invoke()
                }

                it("sends a SIGINT to the container") {
                    verify(api).sendSignalToContainer(container, Signal.SIGINT)
                }
            }
        }
    }
})
