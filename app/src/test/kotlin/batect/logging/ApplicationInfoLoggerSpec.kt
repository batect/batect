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

package batect.logging

import batect.VersionInfo
import batect.docker.DockerClient
import batect.os.SystemInfo
import batect.testutils.InMemoryLogSink
import batect.testutils.hasKeyWithValue
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasSize
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ApplicationInfoLoggerSpec : Spek({
    describe("an application info logger") {
        val logSink = InMemoryLogSink()
        val logger = Logger("applicationInfo", logSink)
        val versionInfo = mock<VersionInfo>()
        val systemInfo = mock<SystemInfo>()
        val environmentVariables = mapOf("PATH" to "/bin:/usr/bin:/usr/local/bin")
        val dockerClient = mock<DockerClient> {
            on { getDockerVersionInfo() } doReturn "Docker version 1.2.3.4"
        }

        val infoLogger = ApplicationInfoLogger(logger, versionInfo, systemInfo, dockerClient, environmentVariables)

        on("logging application information") {
            val commandLineArgs = listOf("some", "values")
            infoLogger.logApplicationInfo(commandLineArgs)

            it("writes a single message to the logger") {
                assertThat(logSink.loggedMessages, hasSize(equalTo(1)))
            }

            it("writes the message at info severity") {
                assertThat(logSink.loggedMessages.single().severity, equalTo(Severity.Info))
            }

            it("writes the message with an explanatory message") {
                assertThat(logSink.loggedMessages.single().message, equalTo("Application started."))
            }

            it("includes the application command line") {
                assertThat(logSink.loggedMessages.single().additionalData, hasKeyWithValue("commandLine", commandLineArgs))
            }

            it("includes application version information") {
                assertThat(logSink.loggedMessages.single().additionalData, hasKeyWithValue("versionInfo", versionInfo))
            }

            it("includes system information") {
                assertThat(logSink.loggedMessages.single().additionalData, hasKeyWithValue("systemInfo", systemInfo))
            }

            it("includes the Docker version") {
                assertThat(logSink.loggedMessages.single().additionalData, hasKeyWithValue("dockerVersionInfo", "Docker version 1.2.3.4"))
            }

            it("includes environment variables") {
                assertThat(logSink.loggedMessages.single().additionalData, hasKeyWithValue("environment", environmentVariables))
            }
        }
    }
})
