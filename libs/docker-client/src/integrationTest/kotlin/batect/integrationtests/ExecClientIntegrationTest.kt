/*
   Copyright 2017-2021 Charles Korn.

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

package batect.integrationtests

import batect.docker.DockerExecResult
import batect.os.Command
import batect.os.Dimensions
import batect.primitives.CancellationContext
import batect.testutils.createForGroup
import batect.testutils.runBeforeGroup
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import okio.sink
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ExecClientIntegrationTest : Spek({
    describe("a Docker exec client") {
        val client by createForGroup { createClient() }

        describe("executing a command in a already running container") {
            val image by runBeforeGroup { client.pull("alpine:3.7") }

            val execResult by runBeforeGroup {
                client.withNetwork { network ->
                    // See https://stackoverflow.com/a/21882119/1668119 for an explanation of this - we need something that waits indefinitely but immediately responds to a SIGTERM by quitting (sh and wait don't do this).
                    val command = listOf("sh", "-c", "trap 'trap - TERM; kill -s TERM -$$' TERM; tail -f /dev/null & wait")

                    client.withContainer(creationRequestForContainer(image, network, command)) { container ->
                        lateinit var execResult: DockerExecResult

                        client.containers.run(container, System.out.sink(), null, true, CancellationContext(), Dimensions(0, 0)) {
                            execResult = client.exec.run(Command.parse("echo -n 'Output from exec'"), container, emptyMap(), false, null, null, null, CancellationContext())

                            client.containers.stop(container)
                        }

                        execResult
                    }
                }
            }

            it("runs the command successfully") {
                assertThat(execResult.exitCode, equalTo(0))
                assertThat(execResult.output, equalTo("Output from exec"))
            }
        }
    }
})
