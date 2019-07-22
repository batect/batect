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

package batect.os

import batect.logging.Logger
import batect.logging.Severity
import batect.testutils.InMemoryLogSink
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.hasMessage
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withAdditionalData
import batect.testutils.withLogMessage
import batect.testutils.withMessage
import batect.testutils.withSeverity
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ProcessRunnerSpec : Spek({
    describe("a process runner") {
        val logSink by createForEachTest { InMemoryLogSink() }
        val logger by createForEachTest { Logger("some.source", logSink) }
        val runner by createForEachTest { ProcessRunner(logger) }

        on("running a process with stdin attached") {
            val command = listOf("bash", "-c", "echo hello world && sleep 1 && echo hello error world 1>&2 && sleep 1 && echo more non-error output && exit 201")
            val result by runForEachTest { runner.runWithStdinAttached(command) }

            it("returns the exit code of the command") {
                assertThat(result.exitCode, equalTo(201))
            }

            it("returns the combined standard output and standard error of the command") {
                assertThat(result.output, equalTo("hello world\nhello error world\nmore non-error output\n"))
            }

            it("logs before running the command") {
                assertThat(logSink, hasMessage(
                    withSeverity(Severity.Debug) and
                        withLogMessage("Starting process.") and
                        withAdditionalData("command", command)))
            }

            it("logs the result of running the command") {
                assertThat(logSink, hasMessage(
                    withSeverity(Severity.Debug) and
                        withLogMessage("Process exited.") and
                        withAdditionalData("command", command) and
                        withAdditionalData("exitCode", 201)
                ))
            }
        }

        describe("running a process and capturing the output") {
            given("the executable exists") {
                given("there is no input to pipe to stdin") {
                    on("running it") {
                        val command = listOf("bash", "-c", "echo hello world && sleep 1 && echo hello error world 1>&2 && sleep 1 && echo more non-error output && exit 201")
                        val result by runForEachTest { runner.runAndCaptureOutput(command) }

                        it("returns the exit code of the command") {
                            assertThat(result.exitCode, equalTo(201))
                        }

                        it("returns the combined standard output and standard error of the command") {
                            assertThat(result.output, equalTo("hello world\nhello error world\nmore non-error output\n"))
                        }

                        it("logs before running the command") {
                            assertThat(logSink, hasMessage(
                                withSeverity(Severity.Debug) and
                                    withLogMessage("Starting process.") and
                                    withAdditionalData("command", command)))
                        }

                        it("logs the result of running the command") {
                            assertThat(logSink, hasMessage(
                                withSeverity(Severity.Debug) and
                                    withLogMessage("Process exited.") and
                                    withAdditionalData("command", command) and
                                    withAdditionalData("exitCode", 201)
                            ))
                        }
                    }
                }

                given("there is some input to pipe to stdin") {
                    on("running it") {
                        val command = listOf("bash", "-c", "tee")
                        val stdin = "This is some input to stdin that will be returned by tee as output"
                        val result by runForEachTest { runner.runAndCaptureOutput(command, stdin) }

                        it("returns the exit code of the command") {
                            assertThat(result.exitCode, equalTo(0))
                        }

                        it("writes the input provided to stdin") {
                            assertThat(result.output, equalTo(stdin))
                        }
                    }
                }
            }

            given("the executable does not exist") {
                on("attempting to run it") {
                    val command = listOf("some-non-existent-app", "--some-arg")

                    it("throws an appropriate exception") {
                        assertThat({ runner.runAndCaptureOutput(command) }, throws<ExecutableDoesNotExistException>(withMessage("The executable 'some-non-existent-app' could not be found or is not executable.")))
                    }
                }
            }

            given("the executable path is not executable") {
                on("attempting to run it") {
                    val command = listOf("/home")

                    it("throws an appropriate exception") {
                        assertThat({ runner.runAndCaptureOutput(command) }, throws<ExecutableDoesNotExistException>(withMessage("The executable '/home' could not be found or is not executable.")))
                    }
                }
            }
        }
    }
})
