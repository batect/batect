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

import batect.docker.ContainerStoppedException
import batect.docker.DockerAPI
import batect.docker.DockerContainer
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.ui.ConsoleDimensions
import batect.ui.ConsoleInfo
import batect.ui.Dimensions
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ContainerTTYManagerSpec : Spek({
    describe("a container TTY manager") {
        val api by createForEachTest { mock<DockerAPI>() }
        val consoleInfo by createForEachTest { mock<ConsoleInfo>() }
        val dimensionsListenerCleanup by createForEachTest { mock<AutoCloseable>() }
        val consoleDimensions by createForEachTest {
            mock<ConsoleDimensions> {
                on { registerListener(any()) } doReturn dimensionsListenerCleanup
            }
        }

        val logger by createLoggerForEachTest()
        val manager by createForEachTest { ContainerTTYManager(api, consoleInfo, consoleDimensions, logger) }

        given("a container") {
            val container = DockerContainer("the-container-id")

            given("the local terminal is a TTY") {
                beforeEachTest { whenever(consoleInfo.stdinIsTTY).doReturn(true) }

                describe("monitoring for terminal size changes") {
                    given("retrieving the current terminal dimensions succeeds") {
                        beforeEachTest { whenever(consoleDimensions.current).doReturn(Dimensions(123, 456)) }

                        given("the container is still running") {
                            on("starting to monitor for terminal size changes") {
                                val returnedCleanup by runForEachTest { manager.monitorForSizeChanges(container) }

                                it("registers a listener for terminal size changes") {
                                    verify(consoleDimensions).registerListener(any())
                                }

                                it("sends the current dimensions to the container") {
                                    verify(api).resizeContainerTTY(container, Dimensions(123, 456))
                                }

                                it("registers the listener before sending the current dimensions") {
                                    inOrder(consoleDimensions, api) {
                                        verify(consoleDimensions).registerListener(any())
                                        verify(api).resizeContainerTTY(container, Dimensions(123, 456))
                                    }
                                }

                                it("returns the cleanup handler from the dimensions listener") {
                                    assertThat(returnedCleanup, equalTo(dimensionsListenerCleanup))
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
                        beforeEachTest { whenever(consoleDimensions.current).doReturn(null as Dimensions?) }

                        val returnedCleanup by runForEachTest { manager.monitorForSizeChanges(container) }

                        it("registers a listener for terminal size changes") {
                            verify(consoleDimensions).registerListener(any())
                        }

                        it("does not send any dimensions to the container") {
                            verify(api, never()).resizeContainerTTY(any(), any())
                        }

                        it("returns the cleanup handler from the dimensions listener") {
                            assertThat(returnedCleanup, equalTo(dimensionsListenerCleanup))
                        }
                    }
                }

                describe("receiving a notification that the terminal's dimensions have changed") {
                    val handlerCaptor by createForEachTest { argumentCaptor<() -> Unit>() }

                    beforeEachTest {
                        manager.monitorForSizeChanges(container)
                        verify(consoleDimensions).registerListener(handlerCaptor.capture())
                    }

                    given("retrieving the current terminal dimensions succeeds") {
                        beforeEachTest { whenever(consoleDimensions.current).doReturn(Dimensions(789, 1234)) }

                        given("the container is still running") {
                            on("invoking the notification listener") {
                                beforeEachTest { handlerCaptor.firstValue.invoke() }

                                it("sends the current dimensions to the container") {
                                    verify(api).resizeContainerTTY(container, Dimensions(789, 1234))
                                }
                            }
                        }

                        given("the container is not still running") {
                            beforeEachTest { whenever(api.resizeContainerTTY(any(), any())).doThrow(ContainerStoppedException("The container is stopped")) }

                            on("invoking the notification listener") {
                                it("does not throw an exception") {
                                    assertThat({ handlerCaptor.firstValue.invoke() }, !throws<Throwable>())
                                }
                            }
                        }
                    }

                    on("not being able to retrieve the current terminal dimensions") {
                        beforeEachTest {
                            whenever(consoleDimensions.current).doReturn(null as Dimensions?)
                            handlerCaptor.firstValue.invoke()
                        }

                        it("does not send any dimensions to the container") {
                            verify(api, never()).resizeContainerTTY(any(), any())
                        }
                    }
                }
            }

            given("the local terminal is not a TTY") {
                beforeEachTest { whenever(consoleInfo.stdinIsTTY).doReturn(false) }

                on("monitoring for terminal size changes") {
                    beforeEachTest { manager.monitorForSizeChanges(container) }

                    it("does not send dimensions to the container") {
                        verify(api, never()).resizeContainerTTY(any(), any())
                    }

                    it("does not install a listener") {
                        verify(consoleDimensions, never()).registerListener(any())
                    }
                }
            }
        }
    }
})
