/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect

import batect.cli.CommandLineOptions
import batect.cli.CommandLineOptionsParser
import batect.cli.CommandLineOptionsParsingResult
import batect.cli.commands.Command
import batect.cli.commands.CommandFactory
import batect.ioc.StreamType
import batect.logging.ApplicationInfoLogger
import batect.logging.Logger
import batect.logging.LoggerFactory
import batect.logging.Severity
import batect.os.ConsoleManager
import batect.os.SystemInfo
import batect.telemetry.AttributeValue
import batect.telemetry.CommonAttributes
import batect.telemetry.CommonEvents
import batect.telemetry.EnvironmentTelemetryCollector
import batect.telemetry.TelemetryManager
import batect.telemetry.TelemetrySessionBuilder
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.logging.InMemoryLogSink
import batect.testutils.logging.hasMessage
import batect.testutils.logging.withException
import batect.testutils.logging.withSeverity
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withPlatformSpecificLineSeparator
import batect.ui.Console
import batect.ui.text.Text
import batect.wrapper.WrapperCache
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object ApplicationSpec : Spek({
    describe("an application") {
        val errorStream by createForEachTest { ByteArrayOutputStream() }
        val commandLineOptionsParser by createForEachTest { mock<CommandLineOptionsParser>() }
        val systemInfo by createForEachTest { mock<SystemInfo>() }
        val telemetrySessionBuilder by createForEachTest { mock<TelemetrySessionBuilder>() }

        val dependencies by createForEachTest {
            DI.direct {
                bind<PrintStream>(StreamType.Error) with instance(PrintStream(errorStream))
                bind<CommandLineOptionsParser>() with instance(commandLineOptionsParser)
                bind<SystemInfo>() with instance(systemInfo)
                bind<TelemetrySessionBuilder>() with instance(telemetrySessionBuilder)
            }
        }

        val application by createForEachTest { Application(dependencies) }
        val args = listOf("some-command", "some-param")

        given("the application is running on a supported operating system") {
            beforeEachTest { whenever(systemInfo.isSupportedOperatingSystem).thenReturn(true) }

            given("parsing the command line arguments succeeds") {
                val applicationInfoLogger by createForEachTest { mock<ApplicationInfoLogger>() }
                val logSink by createForEachTest { InMemoryLogSink() }

                val loggerFactory by createForEachTest {
                    mock<LoggerFactory> {
                        on { createLoggerForClass(Application::class) } doReturn Logger("application", logSink)
                    }
                }

                val commandFactory by createForEachTest { mock<CommandFactory>() }
                val consoleManager by createForEachTest { mock<ConsoleManager>() }
                val errorConsole by createForEachTest { mock<Console>() }
                val wrapperCache by createForEachTest { mock<WrapperCache>() }
                val telemetryManager by createForEachTest { mock<TelemetryManager>() }
                val environmentTelemetryCollector by createForEachTest { mock<EnvironmentTelemetryCollector>() }

                val extendedDependencies by createForEachTest {
                    DI.direct {
                        bind<ApplicationInfoLogger>() with instance(applicationInfoLogger)
                        bind<LoggerFactory>() with instance(loggerFactory)
                        bind<CommandFactory>() with instance(commandFactory)
                        bind<Console>(StreamType.Error) with instance(errorConsole)
                        bind<ConsoleManager>() with instance(consoleManager)
                        bind<WrapperCache>() with instance(wrapperCache)
                        bind<TelemetryManager>() with instance(telemetryManager)
                        bind<EnvironmentTelemetryCollector>() with instance(environmentTelemetryCollector)
                    }
                }

                val options by createForEachTest {
                    mock<CommandLineOptions> {
                        on { extend(dependencies) } doReturn extendedDependencies
                    }
                }

                beforeEachTest {
                    whenever(commandLineOptionsParser.parse(args)).thenReturn(CommandLineOptionsParsingResult.Succeeded(options))
                }

                given("the command executes normally") {
                    val command by createForEachTest {
                        mock<Command> {
                            on { run() } doReturn 123
                        }
                    }

                    beforeEachTest {
                        whenever(commandFactory.createCommand(options, extendedDependencies)).thenReturn(command)
                    }

                    on("running the application") {
                        val exitCode by runForEachTest { application.run(args) }

                        it("does not print anything to the error stream") {
                            assertThat(errorStream.toString(), equalTo(""))
                        }

                        it("returns the exit code from the command") {
                            assertThat(exitCode, equalTo(123))
                        }

                        it("logs information about the application, enables console escape sequences, updates the last used time for the wrapper script and collects information about the environment before running the command") {
                            inOrder(consoleManager, applicationInfoLogger, wrapperCache, environmentTelemetryCollector, command) {
                                verify(applicationInfoLogger).logApplicationInfo(args)
                                verify(consoleManager).enableConsoleEscapeSequences()
                                verify(wrapperCache).setLastUsedForCurrentVersion()
                                verify(environmentTelemetryCollector).collect(command::class)
                                verify(command).run()
                            }
                        }

                        it("finishes the telemetry session after running the command") {
                            inOrder(command, telemetryManager) {
                                verify(command).run()
                                verify(telemetryManager).finishSession(telemetrySessionBuilder)
                            }
                        }

                        it("reports the exit code as part of the telemetry session") {
                            verify(telemetrySessionBuilder).addAttribute("exitCode", 123)
                        }
                    }
                }

                given("the command throws an exception") {
                    val exception = RuntimeException("Everything is broken")
                    val command by createForEachTest {
                        mock<Command> {
                            on { run() } doThrow exception
                        }
                    }

                    beforeEachTest {
                        whenever(commandFactory.createCommand(options, extendedDependencies)).thenReturn(command)
                    }

                    on("running the application") {
                        val exitCode by runForEachTest { application.run(args) }

                        it("prints the exception message to the error console in red") {
                            verify(errorConsole).println(Text.red("java.lang.RuntimeException: Everything is broken"))
                        }

                        it("logs the exception to the log") {
                            assertThat(logSink, hasMessage(withSeverity(Severity.Error) and withException(exception)))
                        }

                        it("returns a non-zero exit code") {
                            assertThat(exitCode, !equalTo(0))
                        }

                        it("reports the exit code as part of the telemetry session") {
                            verify(telemetrySessionBuilder).addAttribute("exitCode", exitCode)
                        }

                        it("reports the exception in telemetry") {
                            verify(telemetrySessionBuilder).addEvent(
                                CommonEvents.UnhandledException,
                                mapOf(
                                    CommonAttributes.Exception to AttributeValue(exception),
                                    CommonAttributes.ExceptionCaughtAt to AttributeValue("batect.Application.runCommand"),
                                    CommonAttributes.IsUserFacingException to AttributeValue(true),
                                ),
                            )
                        }

                        it("finishes the telemetry session after printing the exception message") {
                            inOrder(errorConsole, telemetryManager) {
                                verify(errorConsole).println(any<Text>())
                                verify(telemetryManager).finishSession(telemetrySessionBuilder)
                            }
                        }
                    }
                }
            }

            given("parsing the command line arguments fails") {
                beforeEachTest {
                    whenever(commandLineOptionsParser.parse(args)).thenReturn(CommandLineOptionsParsingResult.Failed("Everything is broken"))
                }

                on("running the application") {
                    val exitCode by runForEachTest { application.run(args) }

                    it("prints the error message to the error stream") {
                        assertThat(errorStream.toString(), equalTo("Everything is broken\n".withPlatformSpecificLineSeparator()))
                    }

                    it("returns a non-zero exit code") {
                        assertThat(exitCode, !equalTo(0))
                    }
                }
            }
        }

        given("the application is running on an unsupported operating system") {
            beforeEachTest { whenever(systemInfo.isSupportedOperatingSystem).thenReturn(false) }

            on("running the application") {
                val exitCode by runForEachTest { application.run(args) }

                it("prints an error message to the error stream") {
                    assertThat(errorStream.toString(), equalTo("Batect only supports Linux, macOS and Windows.\n".withPlatformSpecificLineSeparator()))
                }

                it("returns a non-zero exit code") {
                    assertThat(exitCode, !equalTo(0))
                }
            }
        }
    }
})
