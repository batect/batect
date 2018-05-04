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

package batect

import batect.cli.CommandLineOptions
import batect.cli.CommandLineOptionsParser
import batect.cli.CommandLineOptionsParsingResult
import batect.cli.commands.Command
import batect.cli.commands.CommandFactory
import batect.logging.ApplicationInfoLogger
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.doThrow
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object ApplicationSpec : Spek({
    describe("an application") {
        val errorStream = ByteArrayOutputStream()
        val commandLineOptionsParser = mock<CommandLineOptionsParser>()
        val commandFactory = mock<CommandFactory>()

        val dependencies = Kodein.direct {
            bind<PrintStream>(PrintStreamType.Error) with instance(PrintStream(errorStream))
            bind<CommandLineOptionsParser>() with instance(commandLineOptionsParser)
            bind<CommandFactory>() with instance(commandFactory)
        }

        val application = Application(dependencies)
        val args = listOf("some-command", "some-param")

        beforeEachTest {
            errorStream.reset()
            reset(commandLineOptionsParser)
            reset(commandFactory)
        }

        given("parsing the command line arguments succeeds") {
            val applicationInfoLogger = mock<ApplicationInfoLogger>()

            val extendedDependencies = Kodein.direct {
                bind<ApplicationInfoLogger>() with instance(applicationInfoLogger)
            }

            val options = mock<CommandLineOptions> {
                on { extend(dependencies) } doReturn extendedDependencies
            }

            beforeEachTest {
                whenever(commandLineOptionsParser.parse(args)).thenReturn(CommandLineOptionsParsingResult.Succeeded(options))
            }

            given("the command executes normally") {
                val command = mock<Command> {
                    on { run() } doReturn 123
                }

                beforeEachTest {
                    whenever(commandFactory.createCommand(options, extendedDependencies)).thenReturn(command)
                }

                on("running the application") {
                    val exitCode = application.run(args)

                    it("does not print anything to the error stream") {
                        assertThat(errorStream.toString(), equalTo(""))
                    }

                    it("returns the exit code from the command") {
                        assertThat(exitCode, equalTo(123))
                    }

                    it("logs information about the application before running the command") {
                        inOrder(command, applicationInfoLogger) {
                            verify(applicationInfoLogger).logApplicationInfo(args)
                            verify(command).run()
                        }
                    }
                }
            }

            given("the command throws an exception") {
                val command = mock<Command> {
                    on { run() } doThrow RuntimeException("Everything is broken")
                }

                beforeEachTest {
                    whenever(commandFactory.createCommand(options, extendedDependencies)).thenReturn(command)
                }

                on("running the application") {
                    val exitCode = application.run(args)

                    it("prints the exception message to the error stream") {
                        assertThat(errorStream.toString(), containsSubstring("Everything is broken"))
                    }

                    it("returns a non-zero exit code") {
                        assertThat(exitCode, !equalTo(0))
                    }
                }
            }
        }

        given("parsing the command line arguments fails") {
            beforeEachTest {
                whenever(commandLineOptionsParser.parse(args)).thenReturn(CommandLineOptionsParsingResult.Failed("Everything is broken"))
            }

            on("running the application") {
                val exitCode = application.run(args)

                it("prints the exception message to the error stream") {
                    assertThat(errorStream.toString(), equalTo("Everything is broken\n"))
                }

                it("returns a non-zero exit code") {
                    assertThat(exitCode, !equalTo(0))
                }
            }
        }
    }
})
