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
import batect.ui.ConsoleInfo
import batect.ui.Dimensions
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import jnr.constants.platform.Signal
import jnr.posix.POSIX
import jnr.posix.SignalHandler
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ContainerTTYManagerSpec : Spek({
    describe("a container TTY manager") {
        val api by createForEachTest { mock<DockerAPI>() }
        val consoleInfo by createForEachTest { mock<ConsoleInfo>() }
        val posix by createForEachTest { mock<POSIX>() }
        val manager by createForEachTest { ContainerTTYManager(api, consoleInfo, posix) }

        given("a container") {
            val container = DockerContainer("the-container-id")

            given("the local terminal is a TTY") {
                beforeEachTest { whenever(consoleInfo.stdinIsTTY).doReturn(true) }

                describe("monitoring for terminal size changes") {
                    on("being able to retrieve the current terminal dimensions") {
                        whenever(consoleInfo.dimensions).doReturn(Dimensions(123, 456))

                        manager.monitorForSizeChanges(container)

                        it("registers a signal handler for the SIGWINCH signal") {
                            verify(posix).signal(eq(Signal.SIGWINCH), any())
                        }

                        it("sends the current dimensions to the container") {
                            verify(api).resizeContainerTTY(container, 123, 456)
                        }

                        it("registers the signal handler before sending the current dimensions") {
                            inOrder(posix, api) {
                                verify(posix).signal(eq(Signal.SIGWINCH), any())
                                verify(api).resizeContainerTTY(container, 123, 456)
                            }
                        }
                    }

                    on("not being able to retrieve the current terminal dimensions") {
                        whenever(consoleInfo.dimensions).doReturn(null as Dimensions?)

                        manager.monitorForSizeChanges(container)

                        it("registers a signal handler for the SIGWINCH signal") {
                            verify(posix).signal(eq(Signal.SIGWINCH), any())
                        }

                        it("does not send any dimensions to the container") {
                            verify(api, never()).resizeContainerTTY(any(), any(), any())
                        }
                    }
                }

                describe("receiving a SIGWINCH signal") {
                    val handlerCaptor by createForEachTest { argumentCaptor<SignalHandler>() }

                    beforeEachTest {
                        manager.monitorForSizeChanges(container)
                        verify(posix).signal(eq(Signal.SIGWINCH), handlerCaptor.capture())
                    }

                    on("being able to retrieve the current terminal dimensions") {
                        whenever(consoleInfo.dimensions).doReturn(Dimensions(789, 1234))

                        handlerCaptor.firstValue.handle(Signal.SIGWINCH.value())

                        it("sends the current dimensions to the container") {
                            verify(api).resizeContainerTTY(container, 789, 1234)
                        }
                    }

                    on("not being able to retrieve the current terminal dimensions") {
                        whenever(consoleInfo.dimensions).doReturn(null as Dimensions?)

                        handlerCaptor.firstValue.handle(Signal.SIGWINCH.value())

                        it("does not send any dimensions to the container") {
                            verify(api, never()).resizeContainerTTY(any(), any(), any())
                        }
                    }
                }

                on("stopping monitoring for terminal size changes") {
                    val originalHandler = mock<SignalHandler>()
                    whenever(posix.signal(eq(Signal.SIGWINCH), any())).doReturn(originalHandler)

                    manager.monitorForSizeChanges(container).use { }

                    it("restores the previous SIGINT signal handler") {
                        verify(posix).signal(Signal.SIGWINCH, originalHandler)
                    }
                }
            }

            given("the local terminal is not a TTY") {
                beforeEachTest { whenever(consoleInfo.stdinIsTTY).doReturn(false) }

                on("monitoring for terminal size changes") {
                    manager.monitorForSizeChanges(container)

                    it("does not send dimensions to the container") {
                        verify(api, never()).resizeContainerTTY(any(), any(), any())
                    }

                    it("does not install a signal handler") {
                        verify(posix, never()).signal(any(), any())
                    }
                }

                on("stopping monitoring for terminal size changes") {
                    manager.monitorForSizeChanges(container).use { }

                    it("does not modify any signal handlers") {
                        verify(posix, never()).signal(any(), any())
                    }
                }
            }
        }
    }
})
