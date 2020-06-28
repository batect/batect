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

package batect.docker.client

import batect.docker.DockerContainer
import batect.docker.DockerExecCreationRequest
import batect.docker.DockerExecInstance
import batect.docker.DockerExecInstanceInfo
import batect.docker.DockerExecResult
import batect.docker.UserAndGroup
import batect.docker.api.ExecAPI
import batect.docker.run.ContainerIOStreamer
import batect.docker.run.ContainerOutputStream
import batect.docker.run.OutputConnection
import batect.execution.CancellationContext
import batect.os.Command
import batect.testutils.createForEachTest
import batect.testutils.logging.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import okio.Buffer
import okio.sink
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayOutputStream

object DockerExecClientSpec : Spek({
    describe("a Docker exec client") {
        val api by createForEachTest { mock<ExecAPI>() }
        val ioStreamer by createForEachTest { mock<ContainerIOStreamer>() }
        val logger by createLoggerForEachTest()
        val client by createForEachTest { DockerExecClient(api, ioStreamer, logger) }

        describe("running a command in a running container") {
            val command = Command.parse("do the thing")
            val container = DockerContainer("some-container-id")
            val environmentVariables = mapOf("THING" to "value")
            val privileged = false
            val userAndGroup = UserAndGroup(123, 456)
            val workingDirectory = "/some/dir"
            val outputStream by createForEachTest { ByteArrayOutputStream() }
            val cancellationContext by createForEachTest { mock<CancellationContext>() }

            val instance by createForEachTest { DockerExecInstance("some-exec-instance-id") }
            val containerOutputStream by createForEachTest { mock<ContainerOutputStream>() }

            beforeEachTest {
                whenever(api.create(any(), any())).thenReturn(instance)
                whenever(api.start(any(), any())).thenReturn(containerOutputStream)
                whenever(api.inspect(any())).thenReturn(DockerExecInstanceInfo(123, false))

                whenever(ioStreamer.stream(any(), any(), any())).thenAnswer { invocation ->
                    val outputDestination = invocation.arguments[0] as OutputConnection.Connected
                    val buffer = Buffer()
                    buffer.writeString("some output from the command", Charsets.UTF_8)
                    outputDestination.destination.write(buffer, buffer.size)

                    Unit
                }
            }

            val result by runForEachTest { client.run(command, container, environmentVariables, privileged, userAndGroup, workingDirectory, outputStream.sink(), cancellationContext) }

            val expectedCreationRequest = DockerExecCreationRequest(
                false,
                true,
                true,
                true,
                environmentVariables,
                command.parsedCommand,
                privileged,
                userAndGroup,
                workingDirectory
            )

            it("creates the exec instance with the expected options") {
                verify(api).create(container, expectedCreationRequest)
            }

            it("starts the exec instance with the expected options") {
                verify(api).start(expectedCreationRequest, instance)
            }

            it("passes the cancellation context when streaming output from the command") {
                verify(ioStreamer).stream(any(), any(), eq(cancellationContext))
            }

            it("returns the exit code and output from the command") {
                assertThat(result, equalTo(DockerExecResult(123, "some output from the command")))
            }

            it("writes all output from the container to the provided output stream") {
                assertThat(outputStream.toString(), equalTo("some output from the command"))
            }
        }
    }
})
