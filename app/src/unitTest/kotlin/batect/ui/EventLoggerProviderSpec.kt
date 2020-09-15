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

package batect.ui

import batect.config.Container
import batect.config.Task
import batect.config.TaskRunConfiguration
import batect.execution.ContainerDependencyGraph
import batect.execution.ContainerDependencyGraphNode
import batect.os.ConsoleDimensions
import batect.os.ConsoleInfo
import batect.os.Dimensions
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import batect.ui.containerio.TaskContainerOnlyIOStreamingOptions
import batect.ui.fancy.FancyEventLogger
import batect.ui.fancy.StartupProgressDisplay
import batect.ui.fancy.StartupProgressDisplayProvider
import batect.ui.interleaved.InterleavedContainerIOStreamingOptions
import batect.ui.interleaved.InterleavedEventLogger
import batect.ui.interleaved.InterleavedOutput
import batect.ui.quiet.QuietEventLogger
import batect.ui.simple.SimpleEventLogger
import com.hypirion.io.RevivableInputStream
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isA
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe
import java.io.PrintStream

object EventLoggerProviderSpec : Spek({
    describe("an event logger provider") {
        val failureErrorMessageFormatter = mock<FailureErrorMessageFormatter>()
        val console = mock<Console>()
        val errorConsole = mock<Console>()
        val stdout = mock<PrintStream>()
        val stdin = mock<RevivableInputStream>()
        val consoleInfo by createForEachTest { mock<ConsoleInfo>() }
        val consoleDimensions by createForEachTest { mock<ConsoleDimensions>() }

        val container1 = Container("container-1", imageSourceDoesNotMatter())
        val container2 = Container("container-2", imageSourceDoesNotMatter())
        val taskContainer = Container("task-container", imageSourceDoesNotMatter())
        val task = Task("the-task", TaskRunConfiguration("the-container"))
        val graph = mock<ContainerDependencyGraph> {
            on { allContainers } doReturn setOf(container1, container2)
            on { taskContainerNode } doReturn ContainerDependencyGraphNode(taskContainer, mock(), true, emptySet(), mock())
        }

        val startupProgressDisplay = mock<StartupProgressDisplay>()
        val startupProgressDisplayProvider = mock<StartupProgressDisplayProvider> {
            on { createForDependencyGraph(graph) } doReturn startupProgressDisplay
        }

        fun Suite.itReturnsAQuietEventLogger(loggerCreator: () -> EventLogger) {
            val logger by createForEachTest(loggerCreator)

            it("returns a quiet event logger") {
                assertThat(logger, isA<QuietEventLogger>())
            }

            it("passes the failure error message formatter to the event logger") {
                assertThat((logger as QuietEventLogger).failureErrorMessageFormatter, equalTo(failureErrorMessageFormatter))
            }

            it("passes the error console to the event logger") {
                assertThat((logger as QuietEventLogger).errorConsole, equalTo(errorConsole))
            }

            it("sets the I/O streaming options to the expected value") {
                assertThat(logger.ioStreamingOptions, equalTo(TaskContainerOnlyIOStreamingOptions(taskContainer, stdout, stdin, consoleInfo)))
            }
        }

        fun Suite.itReturnsASimpleEventLogger(loggerCreator: () -> EventLogger) {
            val logger by createForEachTest(loggerCreator)

            it("returns a simple event logger") {
                assertThat(logger, isA<SimpleEventLogger>())
            }

            it("passes the set of containers to the event logger") {
                assertThat((logger as SimpleEventLogger).containers, equalTo(setOf(container1, container2)))
            }

            it("passes the task container to the event logger") {
                assertThat((logger as SimpleEventLogger).taskContainer, equalTo(taskContainer))
            }

            it("passes the failure error message formatter to the event logger") {
                assertThat((logger as SimpleEventLogger).failureErrorMessageFormatter, equalTo(failureErrorMessageFormatter))
            }

            it("passes the console to the event logger") {
                assertThat((logger as SimpleEventLogger).console, equalTo(console))
            }

            it("passes the error console to the event logger") {
                assertThat((logger as SimpleEventLogger).errorConsole, equalTo(errorConsole))
            }

            it("sets the I/O streaming options to the expected value") {
                assertThat(logger.ioStreamingOptions, equalTo(TaskContainerOnlyIOStreamingOptions(taskContainer, stdout, stdin, consoleInfo)))
            }
        }

        fun Suite.itReturnsAFancyEventLogger(startupProgressDisplay: StartupProgressDisplay, loggerCreator: () -> EventLogger) {
            val logger by createForEachTest(loggerCreator)

            it("returns a fancy event logger") {
                assertThat(logger, isA<FancyEventLogger>())
            }

            it("passes the failure error message formatter to the event logger") {
                assertThat((logger as FancyEventLogger).failureErrorMessageFormatter, equalTo(failureErrorMessageFormatter))
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

            it("passes the task container to the event logger") {
                assertThat((logger as FancyEventLogger).taskContainer, equalTo(taskContainer))
            }

            it("sets the I/O streaming options to the expected value") {
                assertThat(logger.ioStreamingOptions, equalTo(TaskContainerOnlyIOStreamingOptions(taskContainer, stdout, stdin, consoleInfo)))
            }
        }

        fun Suite.itReturnsAnInterleavedEventLogger(loggerCreator: () -> EventLogger) {
            val logger by createForEachTest(loggerCreator)

            it("returns an interleaved event logger") {
                assertThat(logger, isA<InterleavedEventLogger>())
            }

            it("passes the task container to the event logger") {
                assertThat((logger as InterleavedEventLogger).taskContainer, equalTo(taskContainer))
            }

            it("passes the set of containers to the event logger") {
                assertThat((logger as InterleavedEventLogger).containers, equalTo(setOf(container1, container2)))
            }

            it("passes the failure error message formatter to the event logger") {
                assertThat((logger as InterleavedEventLogger).failureErrorMessageFormatter, equalTo(failureErrorMessageFormatter))
            }

            it("sets the I/O streaming options to the expected value") {
                val containers = setOf(container1, container2)
                val output = InterleavedOutput("the-task", containers, console)
                assertThat(logger.ioStreamingOptions, equalTo(InterleavedContainerIOStreamingOptions(output)))
            }
        }

        on("when quiet output has been requested") {
            val requestedOutputStyle = OutputStyle.Quiet

            beforeEachTest { whenever(consoleInfo.supportsInteractivity).doReturn(true) }

            val provider by createForEachTest { EventLoggerProvider(failureErrorMessageFormatter, console, errorConsole, stdout, stdin, startupProgressDisplayProvider, consoleInfo, consoleDimensions, requestedOutputStyle, false) }

            itReturnsAQuietEventLogger { provider.getEventLogger(task, graph) }
        }

        on("when simple output has been requested") {
            val requestedOutputStyle = OutputStyle.Simple

            beforeEachTest { whenever(consoleInfo.supportsInteractivity).doReturn(true) }

            val provider by createForEachTest { EventLoggerProvider(failureErrorMessageFormatter, console, errorConsole, stdout, stdin, startupProgressDisplayProvider, consoleInfo, consoleDimensions, requestedOutputStyle, false) }

            itReturnsASimpleEventLogger { provider.getEventLogger(task, graph) }
        }

        on("when fancy output has been requested") {
            val requestedOutputStyle = OutputStyle.Fancy

            beforeEachTest { whenever(consoleInfo.supportsInteractivity).doReturn(true) }

            val provider by createForEachTest { EventLoggerProvider(failureErrorMessageFormatter, console, errorConsole, stdout, stdin, startupProgressDisplayProvider, consoleInfo, consoleDimensions, requestedOutputStyle, false) }

            itReturnsAFancyEventLogger(startupProgressDisplay) { provider.getEventLogger(task, graph) }
        }

        on("when interleaved output has been requested") {
            val requestedOutputStyle = OutputStyle.All

            beforeEachTest { whenever(consoleInfo.supportsInteractivity).doReturn(true) }

            val provider by createForEachTest { EventLoggerProvider(failureErrorMessageFormatter, console, errorConsole, stdout, stdin, startupProgressDisplayProvider, consoleInfo, consoleDimensions, requestedOutputStyle, false) }

            itReturnsAnInterleavedEventLogger { provider.getEventLogger(task, graph) }
        }

        given("no output style has been requested") {
            val requestedOutputStyle: OutputStyle? = null

            given("colored output has been disabled") {
                val disableColorOutput = true

                beforeEachTest {
                    whenever(consoleInfo.supportsInteractivity).doReturn(true)
                    whenever(consoleDimensions.current).doReturn(Dimensions(123, 456))
                }

                val provider by createForEachTest { EventLoggerProvider(failureErrorMessageFormatter, console, errorConsole, stdout, stdin, startupProgressDisplayProvider, consoleInfo, consoleDimensions, requestedOutputStyle, disableColorOutput) }

                itReturnsASimpleEventLogger { provider.getEventLogger(task, graph) }
            }

            given("colored output has not been disabled") {
                val disableColorOutput = false

                given("the console supports interactivity") {
                    beforeEachTest { whenever(consoleInfo.supportsInteractivity).doReturn(true) }

                    given("the console's dimensions are available") {
                        beforeEachTest { whenever(consoleDimensions.current).doReturn(Dimensions(123, 456)) }

                        val provider by createForEachTest { EventLoggerProvider(failureErrorMessageFormatter, console, errorConsole, stdout, stdin, startupProgressDisplayProvider, consoleInfo, consoleDimensions, requestedOutputStyle, disableColorOutput) }

                        itReturnsAFancyEventLogger(startupProgressDisplay) { provider.getEventLogger(task, graph) }
                    }

                    given("the console's dimensions are not available") {
                        beforeEachTest { whenever(consoleDimensions.current).doReturn(null) }

                        val provider by createForEachTest { EventLoggerProvider(failureErrorMessageFormatter, console, errorConsole, stdout, stdin, startupProgressDisplayProvider, consoleInfo, consoleDimensions, requestedOutputStyle, disableColorOutput) }

                        itReturnsASimpleEventLogger { provider.getEventLogger(task, graph) }
                    }
                }

                given("the console does not support interactivity") {
                    beforeEachTest {
                        whenever(consoleInfo.supportsInteractivity).doReturn(false)
                        whenever(consoleDimensions.current).doReturn(Dimensions(123, 456))
                    }

                    val provider by createForEachTest { EventLoggerProvider(failureErrorMessageFormatter, console, errorConsole, stdout, stdin, startupProgressDisplayProvider, consoleInfo, consoleDimensions, requestedOutputStyle, disableColorOutput) }

                    itReturnsASimpleEventLogger { provider.getEventLogger(task, graph) }
                }
            }
        }
    }
})
