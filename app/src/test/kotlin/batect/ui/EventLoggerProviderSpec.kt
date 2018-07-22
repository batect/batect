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

package batect.ui

import batect.config.Container
import batect.execution.ContainerDependencyGraph
import batect.execution.ContainerDependencyGraphNode
import batect.execution.RunOptions
import batect.testutils.imageSourceDoesNotMatter
import batect.ui.fancy.FancyEventLogger
import batect.ui.fancy.StartupProgressDisplay
import batect.ui.fancy.StartupProgressDisplayProvider
import batect.ui.quiet.QuietEventLogger
import batect.ui.simple.SimpleEventLogger
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.TestContainer
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object EventLoggerProviderSpec : Spek({
    describe("an event logger provider") {
        val failureErrorMessageFormatter = mock<FailureErrorMessageFormatter>()
        val console = mock<Console>()
        val errorConsole = mock<Console>()
        val container1 = Container("container-1", imageSourceDoesNotMatter())
        val container2 = Container("container-2", imageSourceDoesNotMatter())
        val graph = mock<ContainerDependencyGraph> {
            on { allNodes } doReturn setOf(
                ContainerDependencyGraphNode(container1, null, emptyMap(), emptySet(), false, emptySet(), mock()),
                ContainerDependencyGraphNode(container2, null, emptyMap(), emptySet(), false, emptySet(), mock())
            )
        }

        val startupProgressDisplay = mock<StartupProgressDisplay>()
        val startupProgressDisplayProvider = mock<StartupProgressDisplayProvider> {
            on { createForDependencyGraph(graph) } doReturn startupProgressDisplay
        }

        val runOptions = mock<RunOptions>()

        fun TestContainer.itReturnsAQuietEventLogger(logger: EventLogger) {
            it("returns a quiet event logger") {
                assertThat(logger, isA<QuietEventLogger>())
            }

            it("passes the failure error message formatter to the event logger") {
                assertThat((logger as QuietEventLogger).failureErrorMessageFormatter, equalTo(failureErrorMessageFormatter))
            }

            it("passes the run options to the event logger") {
                assertThat((logger as QuietEventLogger).runOptions, equalTo(runOptions))
            }

            it("passes the error console to the event logger") {
                assertThat((logger as QuietEventLogger).errorConsole, equalTo(errorConsole))
            }
        }

        fun TestContainer.itReturnsASimpleEventLogger(logger: EventLogger) {
            it("returns a simple event logger") {
                assertThat(logger, isA<SimpleEventLogger>())
            }

            it("passes the set of containers to the event logger") {
                assertThat((logger as SimpleEventLogger).containers, equalTo(setOf(container1, container2)))
            }

            it("passes the failure error message formatter to the event logger") {
                assertThat((logger as SimpleEventLogger).failureErrorMessageFormatter, equalTo(failureErrorMessageFormatter))
            }

            it("passes the run options to the event logger") {
                assertThat((logger as SimpleEventLogger).runOptions, equalTo(runOptions))
            }

            it("passes the console to the event logger") {
                assertThat((logger as SimpleEventLogger).console, equalTo(console))
            }

            it("passes the error console to the event logger") {
                assertThat((logger as SimpleEventLogger).errorConsole, equalTo(errorConsole))
            }
        }

        fun TestContainer.itReturnsAFancyEventLogger(logger: EventLogger, startupProgressDisplay: StartupProgressDisplay) {
            it("returns a fancy event logger") {
                assertThat(logger, isA<FancyEventLogger>())
            }

            it("passes the failure error message formatter to the event logger") {
                assertThat((logger as FancyEventLogger).failureErrorMessageFormatter, equalTo(failureErrorMessageFormatter))
            }

            it("passes the run options to the event logger") {
                assertThat((logger as FancyEventLogger).runOptions, equalTo(runOptions))
            }

            it("passes the console to the event logger") {
                assertThat((logger as FancyEventLogger).console, equalTo(console))
            }

            it("passes the error console to the event logger") {
                assertThat((logger as FancyEventLogger).errorConsole, equalTo(errorConsole))
            }

            it("passes the startup progress display to the event logger") {
                assertThat((logger as FancyEventLogger).startupProgressDisplay, equalTo(startupProgressDisplay))
            }
        }

        on("when quiet output has been requested") {
            val requestedOutputStyle = OutputStyle.Quiet

            val consoleInfo = mock<ConsoleInfo> {
                on { supportsInteractivity } doReturn true
            }

            val provider = EventLoggerProvider(failureErrorMessageFormatter, console, errorConsole, startupProgressDisplayProvider, consoleInfo, requestedOutputStyle, false)
            val logger = provider.getEventLogger(graph, runOptions)

            itReturnsAQuietEventLogger(logger)
        }

        on("when simple output has been requested") {
            val requestedOutputStyle = OutputStyle.Simple

            val consoleInfo = mock<ConsoleInfo> {
                on { supportsInteractivity } doReturn true
            }

            val provider = EventLoggerProvider(failureErrorMessageFormatter, console, errorConsole, startupProgressDisplayProvider, consoleInfo, requestedOutputStyle, false)
            val logger = provider.getEventLogger(graph, runOptions)

            itReturnsASimpleEventLogger(logger)
        }

        on("when fancy output has been requested") {
            val requestedOutputStyle = OutputStyle.Fancy

            val consoleInfo = mock<ConsoleInfo> {
                on { supportsInteractivity } doReturn true
            }

            val provider = EventLoggerProvider(failureErrorMessageFormatter, console, errorConsole, startupProgressDisplayProvider, consoleInfo, requestedOutputStyle, false)
            val logger = provider.getEventLogger(graph, runOptions)

            itReturnsAFancyEventLogger(logger, startupProgressDisplay)
        }

        given("no output style has been requested") {
            val requestedOutputStyle: OutputStyle? = null

            on("when colored output has been disabled") {
                val disableColorOutput = true

                val consoleInfo = mock<ConsoleInfo> {
                    on { supportsInteractivity } doReturn true
                }

                val provider = EventLoggerProvider(failureErrorMessageFormatter, console, errorConsole, startupProgressDisplayProvider, consoleInfo, requestedOutputStyle, disableColorOutput)
                val logger = provider.getEventLogger(graph, runOptions)

                itReturnsASimpleEventLogger(logger)
            }

            given("colored output has not been disabled") {
                val disableColorOutput = false

                on("when the console supports interactivity") {
                    val consoleInfo = mock<ConsoleInfo> {
                        on { supportsInteractivity } doReturn true
                    }

                    val provider = EventLoggerProvider(failureErrorMessageFormatter, console, errorConsole, startupProgressDisplayProvider, consoleInfo, requestedOutputStyle, disableColorOutput)
                    val logger = provider.getEventLogger(graph, runOptions)

                    itReturnsAFancyEventLogger(logger, startupProgressDisplay)
                }

                on("when the console does not support interactivity") {
                    val consoleInfo = mock<ConsoleInfo> {
                        on { supportsInteractivity } doReturn false
                    }

                    val provider = EventLoggerProvider(failureErrorMessageFormatter, console, errorConsole, startupProgressDisplayProvider, consoleInfo, requestedOutputStyle, disableColorOutput)
                    val logger = provider.getEventLogger(graph, runOptions)

                    itReturnsASimpleEventLogger(logger)
                }
            }
        }
    }
})
