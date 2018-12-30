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

import batect.docker.ContainerStoppedException
import batect.docker.DockerAPI
import batect.docker.DockerContainer
import batect.os.SignalListener
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.ui.ConsoleInfo
import batect.ui.Dimensions
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import jnr.constants.platform.Signal
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ContainerTTYManagerSpec : Spek({
    describe("a container TTY manager") {
        val api by createForEachTest { mock<DockerAPI>() }
        val consoleInfo by createForEachTest { mock<ConsoleInfo>() }
        val signalCleanup by createForEachTest { mock<AutoCloseable>() }
        val signalListener by createForEachTest {
            mock<SignalListener> {
                on { start(any(), any()) } doReturn signalCleanup
            }
        }

        val logger by createLoggerForEachTest()
        val manager by createForEachTest { ContainerTTYManager(api, consoleInfo, signalListener, logger) }

        given("a container") {
            val container = DockerContainer("the-container-id")

            given("the local terminal is a TTY") {
                beforeEachTest { whenever(consoleInfo.stdinIsTTY).doReturn(true) }

                describe("monitoring for terminal size changes") {
                    given("retrieving the current terminal dimensions succeeds") {
                        beforeEachTest { whenever(consoleInfo.dimensions).doReturn(Dimensions(123, 456)) }

                        given("the container is still running") {
                            on("starting to monitor for terminal size changes") {
                                val returnedCleanup = manager.monitorForSizeChanges(container)

                                it("registers a signal handler for the SIGWINCH signal") {
                                    verify(signalListener).start(eq(Signal.SIGWINCH), any())
                                }

                                it("sends the current dimensions to the container") {
                                    verify(api).resizeContainerTTY(container, Dimensions(123, 456))
                                }

                                it("registers the signal handler before sending the current dimensions") {
                                    inOrder(signalListener, api) {
                                        verify(signalListener).start(eq(Signal.SIGWINCH), any())
                                        verify(api).resizeContainerTTY(container, Dimensions(123, 456))
                                    }
                                }

                                it("returns the cleanup handler from the signal listener") {
                                    assertThat(returnedCleanup, equalTo(signalCleanup))
                                }
                            }
                        }

                        given("the container is not still running") {
                            beforeEachTest { whenever(api.resizeContainerTTY(any(), any())).doThrow(ContainerStoppedException("The container is stopped")) }

                            on("starting to monitor for terminal size changes") {
                                it("does not throw an exception") {
                                    assertThat({ manager.monitorForSizeChanges(container) }, !throws<Throwable>())
                                }
                            }
                        }
                    }

                    on("not being able to retrieve the current terminal dimensions") {
                        whenever(consoleInfo.dimensions).doReturn(null as Dimensions?)

                        val returnedCleanup = manager.monitorForSizeChanges(container)

                        it("registers a signal handler for the SIGWINCH signal") {
                            verify(signalListener).start(eq(Signal.SIGWINCH), any())
                        }

                        it("does not send any dimensions to the container") {
                            verify(api, never()).resizeContainerTTY(any(), any())
                        }

                        it("returns the cleanup handler from the signal listener") {
                            assertThat(returnedCleanup, equalTo(signalCleanup))
                        }
                    }
                }

                describe("receiving a SIGWINCH signal") {
                    val handlerCaptor by createForEachTest { argumentCaptor<() -> Unit>() }

                    beforeEachTest {
                        manager.monitorForSizeChanges(container)
                        verify(signalListener).start(eq(Signal.SIGWINCH), handlerCaptor.capture())
                    }

                    given("retrieving the current terminal dimensions succeeds") {
                        beforeEachTest { whenever(consoleInfo.dimensions).doReturn(Dimensions(789, 1234)) }

                        given("the container is still running") {
                            on("invoking the signal handler") {
                                handlerCaptor.firstValue.invoke()

                                it("sends the current dimensions to the container") {
                                    verify(api).resizeContainerTTY(container, Dimensions(789, 1234))
                                }
                            }
                        }

                        given("the container is not still running") {
                            beforeEachTest { whenever(api.resizeContainerTTY(any(), any())).doThrow(ContainerStoppedException("The container is stopped")) }

                            on("invoking the signal handler") {
                                it("does not throw an exception") {
                                    assertThat({ handlerCaptor.firstValue.invoke() }, !throws<Throwable>())
                                }
                            }
                        }
                    }

                    on("not being able to retrieve the current terminal dimensions") {
                        whenever(consoleInfo.dimensions).doReturn(null as Dimensions?)

                        handlerCaptor.firstValue.invoke()

                        it("does not send any dimensions to the container") {
                            verify(api, never()).resizeContainerTTY(any(), any())
                        }
                    }
                }
            }

            given("the local terminal is not a TTY") {
                beforeEachTest { whenever(consoleInfo.stdinIsTTY).doReturn(false) }

                on("monitoring for terminal size changes") {
                    manager.monitorForSizeChanges(container)

                    it("does not send dimensions to the container") {
                        verify(api, never()).resizeContainerTTY(any(), any())
                    }

                    it("does not install a signal handler") {
                        verify(signalListener, never()).start(any(), any())
                    }
                }
            }
        }
    }
})
