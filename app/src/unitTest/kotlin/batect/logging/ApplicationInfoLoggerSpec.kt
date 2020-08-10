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

package batect.logging

import batect.VersionInfo
import batect.docker.client.DockerSystemInfoClient
import batect.docker.client.DockerVersionInfoRetrievalResult
import batect.git.GitClient
import batect.git.GitVersionRetrievalResult
import batect.os.ConsoleInfo
import batect.os.HostEnvironmentVariables
import batect.os.OperatingSystem
import batect.os.SystemInfo
import batect.testutils.logging.InMemoryLogSink
import batect.testutils.logging.hasMessage
import batect.testutils.logging.withAdditionalData
import batect.testutils.logging.withLogMessage
import batect.testutils.logging.withSeverity
import batect.testutils.on
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasSize
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ApplicationInfoLoggerSpec : Spek({
    describe("an application info logger") {
        val logSink = InMemoryLogSink()
        val logger = Logger("applicationInfo", logSink)
        val versionInfo = VersionInfo()
        val fileSystem = Jimfs.newFileSystem(Configuration.unix())
        val systemInfo = SystemInfo(OperatingSystem.Linux, "1.2.3", "line-separator", "4.5.6", "me", fileSystem.getPath("/home"), fileSystem.getPath("/tmp"))
        val consoleInfo = mock<ConsoleInfo>()
        val environmentVariables = HostEnvironmentVariables("PATH" to "/bin:/usr/bin:/usr/local/bin")
        val dockerSystemInfoClient = mock<DockerSystemInfoClient> {
            on { getDockerVersionInfo() } doReturn DockerVersionInfoRetrievalResult.Failed("Docker version 1.2.3.4")
        }

        val gitClient = mock<GitClient> {
            on { version } doReturn GitVersionRetrievalResult.Succeeded("1.2.3")
        }

        val infoLogger = ApplicationInfoLogger(logger, versionInfo, systemInfo, consoleInfo, dockerSystemInfoClient, gitClient, environmentVariables)

        on("logging application information") {
            val commandLineArgs = listOf("some", "values")
            infoLogger.logApplicationInfo(commandLineArgs)

            it("writes a single message to the logger") {
                assertThat(logSink.loggedMessages, hasSize(equalTo(1)))
            }

            it("writes the message at info severity") {
                assertThat(logSink, hasMessage(withSeverity(Severity.Info)))
            }

            it("writes the message with an explanatory message") {
                assertThat(logSink, hasMessage(withLogMessage("Application started.")))
            }

            it("includes the application command line") {
                assertThat(logSink, hasMessage(withAdditionalData("commandLine", commandLineArgs)))
            }

            it("includes application version information") {
                assertThat(logSink, hasMessage(withAdditionalData("versionInfo", versionInfo)))
            }

            it("includes system information") {
                assertThat(logSink, hasMessage(withAdditionalData("systemInfo", systemInfo)))
            }

            it("includes console information") {
                assertThat(logSink, hasMessage(withAdditionalData("consoleInfo", consoleInfo)))
            }

            it("includes the Docker version") {
                assertThat(logSink, hasMessage(withAdditionalData("dockerVersionInfo", "(Docker version 1.2.3.4)")))
            }

            it("includes the Git version") {
                assertThat(logSink, hasMessage(withAdditionalData("gitVersion", "1.2.3")))
            }

            it("includes environment variables") {
                assertThat(logSink, hasMessage(withAdditionalData("environment", environmentVariables)))
            }
        }
    }
})
