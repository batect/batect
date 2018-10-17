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

package batect.docker.run

import batect.docker.DockerAPI
import batect.docker.DockerContainer
import batect.testutils.createForEachTest
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import jnr.constants.platform.Signal
import jnr.posix.POSIX
import jnr.posix.SignalHandler
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ContainerKillerSpec : Spek({
    describe("a container killer") {
        val api by createForEachTest { mock<DockerAPI>() }
        val posix by createForEachTest { mock<POSIX>() }
        val killer by createForEachTest { ContainerKiller(api, posix) }

        describe("killing a container when a SIGINT is received") {
            val container = DockerContainer("the-container")

            on("starting monitoring") {
                killer.killContainerOnSigint(container)

                it("registers a signal handler for the SIGINT signal") {
                    verify(posix).signal(eq(Signal.SIGINT), any())
                }
            }

            on("a SIGINT being received") {
                val handlerCaptor = argumentCaptor<SignalHandler>()

                killer.killContainerOnSigint(container)

                verify(posix).signal(eq(Signal.SIGINT), handlerCaptor.capture())
                handlerCaptor.firstValue.handle(Signal.SIGINT.value())

                it("sends a SIGINT to the container") {
                    verify(api).sendSignalToContainer(container, Signal.SIGINT)
                }
            }

            on("stopping monitoring") {
                val originalHandler = mock<SignalHandler>()
                whenever(posix.signal(eq(Signal.SIGINT), any())).doReturn(originalHandler)

                killer.killContainerOnSigint(container).use { }

                it("restores the previous SIGINT signal handler") {
                    verify(posix).signal(Signal.SIGINT, originalHandler)
                }
            }
        }
    }
})
