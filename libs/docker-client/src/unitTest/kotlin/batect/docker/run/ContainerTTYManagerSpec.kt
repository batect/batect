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

package batect.docker.run

import batect.docker.DockerContainer
import batect.docker.api.ContainerStoppedException
import batect.docker.api.ContainersAPI
import batect.os.ConsoleDimensions
import batect.os.Dimensions
import batect.testutils.createForEachTest
import batect.testutils.doesNotThrow
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.logging.createLoggerForEachTestWithoutCustomSerializers
import batect.testutils.on
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
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
        val frameDimensions = Dimensions(23, 56)
        val api by createForEachTest { mock<ContainersAPI>() }
        val dimensionsListenerCleanup by createForEachTest { mock<AutoCloseable>() }
        val consoleDimensions by createForEachTest {
            mock<ConsoleDimensions> {
                on { registerListener(any()) } doReturn dimensionsListenerCleanup
            }
        }

        val logger by createLoggerForEachTestWithoutCustomSerializers()
        val manager by createForEachTest { ContainerTTYManager(api, consoleDimensions, logger) }

        given("a container") {
            val container = DockerContainer("the-container-id")

            describe("monitoring for terminal size changes") {
                given("retrieving the current terminal dimensions succeeds") {
                    beforeEachTest { whenever(consoleDimensions.current).doReturn(Dimensions(123, 456)) }

                    given("the container is still running") {
                        on("starting to monitor for terminal size changes") {
                            val returnedCleanup by runForEachTest { manager.monitorForSizeChanges(container, frameDimensions) }

                            it("registers a listener for terminal size changes") {
                                verify(consoleDimensions).registerListener(any())
                            }

                            it("sends the current dimensions less the frame size to the container") {
                                verify(api).resizeTTY(container, Dimensions(100, 400))
                            }

                            it("registers the listener before sending the current dimensions") {
                                inOrder(consoleDimensions, api) {
                                    verify(consoleDimensions).registerListener(any())
                                    verify(api).resizeTTY(container, Dimensions(100, 400))
                                }
                            }

                            it("returns the cleanup handler from the dimensions listener") {
                                assertThat(returnedCleanup, equalTo(dimensionsListenerCleanup))
                            }
                        }
                    }

                    given("the container is not still running") {
                        beforeEachTest { whenever(api.resizeTTY(any(), any())).doThrow(ContainerStoppedException("The container is stopped")) }

                        on("starting to monitor for terminal size changes") {
                            it("does not throw an exception") {
                                assertThat({ manager.monitorForSizeChanges(container, frameDimensions) }, doesNotThrow())
                            }
                        }
                    }
                }

                on("not being able to retrieve the current terminal dimensions") {
                    beforeEachTest { whenever(consoleDimensions.current).doReturn(null as Dimensions?) }

                    val returnedCleanup by runForEachTest { manager.monitorForSizeChanges(container, frameDimensions) }

                    it("registers a listener for terminal size changes") {
                        verify(consoleDimensions).registerListener(any())
                    }

                    it("does not send any dimensions to the container") {
                        verify(api, never()).resizeTTY(any(), any())
                    }

                    it("returns the cleanup handler from the dimensions listener") {
                        assertThat(returnedCleanup, equalTo(dimensionsListenerCleanup))
                    }
                }
            }

            describe("receiving a notification that the terminal's dimensions have changed") {
                val handlerCaptor by createForEachTest { argumentCaptor<() -> Unit>() }

                beforeEachTest {
                    manager.monitorForSizeChanges(container, frameDimensions)
                    verify(consoleDimensions).registerListener(handlerCaptor.capture())
                }

                given("retrieving the current terminal dimensions succeeds") {
                    beforeEachTest { whenever(consoleDimensions.current).doReturn(Dimensions(789, 1234)) }

                    given("the container is still running") {
                        on("invoking the notification listener") {
                            beforeEachTest { handlerCaptor.firstValue.invoke() }

                            it("sends the current dimensions to the container") {
                                verify(api).resizeTTY(container, Dimensions(766, 1178))
                            }
                        }
                    }

                    given("the container is not still running") {
                        beforeEachTest { whenever(api.resizeTTY(any(), any())).doThrow(ContainerStoppedException("The container is stopped")) }

                        on("invoking the notification listener") {
                            it("does not throw an exception") {
                                assertThat({ handlerCaptor.firstValue.invoke() }, doesNotThrow())
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
                        verify(api, never()).resizeTTY(any(), any())
                    }
                }
            }
        }
    }
})
