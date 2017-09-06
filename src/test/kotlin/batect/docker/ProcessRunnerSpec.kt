/*
   Copyright 2017 Charles Korn.

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

package batect.docker

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.File

object ProcessRunnerSpec : Spek({
    describe("a process runner") {
        val runner = ProcessRunner()

        on("running a process") {
            it("executes the command given") {
                val filePath = File.createTempFile("processrunner", ".tmp")
                filePath.deleteOnExit()

                val command = listOf("rm", filePath.absolutePath)
                runner.run(command)

                assertThat(filePath.exists(), equalTo(false))
            }

            it("returns the exit code of the command") {
                val command = listOf("sh", "-c", "exit 123")
                val exitCode = runner.run(command)

                assertThat(exitCode, equalTo(123))
            }
        }

        on("running a process and capturing the output") {
            val command = listOf("sh", "-c", "echo hello world && echo hello error world 2>&1 && echo more non-error output && exit 201")
            val result = runner.runAndCaptureOutput(command)

            it("returns the exit code of the command") {
                assertThat(result.exitCode, equalTo(201))
            }

            it("returns the combined standard output and standard error of the command") {
                assertThat(result.output, equalTo("hello world\nhello error world\nmore non-error output\n"))
            }
        }

        describe("running a process and processing the output") {
            on("the processing not requesting an early termination of the process") {
                val command = listOf("sh", "-c", "echo line1 && echo line2 2>&1 && printf lastLineWithoutTrailingNewLine && exit 123")
                val linesProcessed = mutableListOf<String>()

                val result = runner.runAndProcessOutput<Any>(command) { line ->
                    linesProcessed.add(line)
                    Continue()
                }

                it("calls the processing method provided for each line written to stdout or stderr") {
                    assertThat(linesProcessed, equalTo(listOf("line1", "line2", "lastLineWithoutTrailingNewLine")))
                }

                it("returns the exit code of the process") {
                    assertThat(result, equalTo(Exited<Any>(123) as RunAndProcessOutputResult<Any>))
                }
            }

            on("the processing requesting an early termination of the process") {
                val tempFile = File.createTempFile("processrunner", ".tmp")
                tempFile.deleteOnExit()

                val command = listOf("sh", "-c", "echo line1 && echo line2 2>&1 && sleep 0.1 && echo This should never happen > '$tempFile' && exit 123")
                val linesProcessed = mutableListOf<String>()

                val result = runner.runAndProcessOutput<String>(command) { line ->
                    linesProcessed.add(line)

                    if (line == "line2") {
                        KillProcess("I saw line 2")
                    } else {
                        Continue()
                    }
                }

                it("calls the processing method provided for each line written to stdout or stderr") {
                    assertThat(linesProcessed, equalTo(listOf("line1", "line2")))
                }

                it("returns the value provided by the processing method") {
                    assertThat(result, equalTo(KilledDuringProcessing("I saw line 2") as RunAndProcessOutputResult<String>))
                }

                it("kills the process when instructed to do so by the processing method") {
                    // FIXME This test has a race condition, but I can't think of any better way to test this.
                    Thread.sleep(200)
                    assertThat(tempFile.length(), equalTo(0L))
                }
            }
        }
    }
})
