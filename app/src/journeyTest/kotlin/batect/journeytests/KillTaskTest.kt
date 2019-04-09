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

package batect.journeytests

import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import jnr.constants.platform.Signal
import jnr.posix.POSIXFactory
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

object KillTaskTest : Spek({
    describe("a task that exits when sent a SIGINT (Ctrl-C)") {

        on("starting the task and sending it a Ctrl-C") {
            var finished = false

            beforeGroup {
                val applicationPath: Path = Paths.get("build/install/app-shadow/bin/batect").toAbsolutePath()
                val testDirectory: Path = Paths.get("src/journeyTest/resources", "container-with-signal-issue").toAbsolutePath()

                val builder = ProcessBuilder(listOf(applicationPath.toString(), "api"))
                    .directory(testDirectory.toFile())
                    .redirectErrorStream(true)

                val process = builder.start()
                val processId = process.getProcessId()

                val output = BufferedReader(InputStreamReader(process.inputStream))
                val outputSoFar = StringBuilder()


                readLoop@ while (true) {
                    when (val line = output.readLine()) {
                        null -> throw RuntimeException("Container did not start before process finished. Output from container was: $outputSoFar")
                        "Server ready." -> {
                            POSIXFactory.getNativePOSIX().kill(processId, Signal.SIGINT.value())

                            break@readLoop
                        }
                        else -> outputSoFar.appendln(line)
                    }
                }

                finished = process.waitFor(30, TimeUnit.SECONDS)
            }

            it("kills the container") {
                assertThat("The container was not correctly killed", finished, equalTo(true))
            }
        }
    }
})

private fun Process.getProcessId(): Long {
    if (this.javaClass.name != "java.lang.UNIXProcess") {
        throw UnsupportedOperationException("Unknown process type: ${this.javaClass.name}")
    }

    val field = this.javaClass.getDeclaredField("pid")
    field.setAccessible(true)
    val pid = field.getLong(this)
    field.setAccessible(false)

    return pid
}
