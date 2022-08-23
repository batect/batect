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

package batect.cli.commands

import batect.VersionInfo
import batect.docker.DockerClientFactory
import batect.dockerclient.DaemonVersionInformation
import batect.dockerclient.DockerClient
import batect.dockerclient.DockerClientException
import batect.git.GitClient
import batect.git.GitVersionRetrievalResult
import batect.os.SystemInfo
import batect.primitives.Version
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.on
import batect.testutils.runForEachTest
import batect.testutils.withPlatformSpecificLineSeparator
import batect.updates.UpdateNotifier
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object VersionInfoCommandSpec : Spek({
    describe("a 'version info' command") {
        val versionInfo = mock<VersionInfo> {
            on { version } doReturn Version(1, 2, 3)
            on { buildDate } doReturn "THE BUILD DATE"
            on { gitCommitHash } doReturn "THE BUILD COMMIT"
            on { gitCommitDate } doReturn "COMMIT DATE"
        }

        val systemInfo = mock<SystemInfo> {
            on { jvmVersion } doReturn "THE JVM VERSION"
            on { osSummary } doReturn "THE OS VERSION"
        }

        val gitClient = mock<GitClient> {
            on { version } doReturn GitVersionRetrievalResult.Failed("GIT VERSION INFO")
        }

        val outputStream by createForEachTest { ByteArrayOutputStream() }
        val updateNotifier by createForEachTest { mock<UpdateNotifier>() }
        val logger by createLoggerForEachTest()

        on("when retrieving information about the Docker daemon succeeds") {
            val dockerClient = mock<DockerClient> {
                onBlocking { getDaemonVersionInformation() } doReturn DaemonVersionInformation("20.10.11", "1.41", "1.12", "abc123", "linux", "amd64", false)
            }

            val dockerClientFactory = mock<DockerClientFactory> {
                on { create() } doReturn dockerClient
            }

            val command by createForEachTest { VersionInfoCommand(versionInfo, PrintStream(outputStream), systemInfo, dockerClientFactory, gitClient, updateNotifier, logger) }
            val exitCode by runForEachTest { command.run() }

            it("prints version information") {
                assertThat(
                    outputStream.toString(),
                    equalTo(
                        """
                        |Batect version:    1.2.3
                        |Built:             THE BUILD DATE
                        |Built from commit: THE BUILD COMMIT (commit date: COMMIT DATE)
                        |JVM version:       THE JVM VERSION
                        |OS version:        THE OS VERSION
                        |Docker version:    20.10.11 (API version: 1.41, minimum supported API version: 1.12, commit: abc123, operating system: 'linux', architecture: 'amd64', experimental: false)
                        |Git version:       (GIT VERSION INFO)
                        |
                        |For documentation and further information on Batect, visit https://github.com/batect/batect.
                        |
                        |
                        """.trimMargin().withPlatformSpecificLineSeparator()
                    )
                )
            }

            it("returns a zero exit code") {
                assertThat(exitCode, equalTo(0))
            }

            it("notifies the user of any updates") {
                verify(updateNotifier).run()
            }
        }

        on("when retrieving information about the Docker daemon fails") {
            val dockerClient = mock<DockerClient> {
                onBlocking { getDaemonVersionInformation() } doThrow DockerClientException("Could not get version information")
            }

            val dockerClientFactory = mock<DockerClientFactory> {
                on { create() } doReturn dockerClient
            }

            val command by createForEachTest { VersionInfoCommand(versionInfo, PrintStream(outputStream), systemInfo, dockerClientFactory, gitClient, updateNotifier, logger) }
            val exitCode by runForEachTest { command.run() }

            it("prints version information") {
                assertThat(
                    outputStream.toString(),
                    equalTo(
                        """
                        |Batect version:    1.2.3
                        |Built:             THE BUILD DATE
                        |Built from commit: THE BUILD COMMIT (commit date: COMMIT DATE)
                        |JVM version:       THE JVM VERSION
                        |OS version:        THE OS VERSION
                        |Docker version:    (could not get Docker version information because DockerClientException was thrown: Could not get version information)
                        |Git version:       (GIT VERSION INFO)
                        |
                        |For documentation and further information on Batect, visit https://github.com/batect/batect.
                        |
                        |
                        """.trimMargin().withPlatformSpecificLineSeparator()
                    )
                )
            }

            it("returns a zero exit code") {
                assertThat(exitCode, equalTo(0))
            }

            it("notifies the user of any updates") {
                verify(updateNotifier).run()
            }
        }

        on("when creating the Docker client fails") {
            val dockerClientFactory = mock<DockerClientFactory> {
                on { create() } doThrow DockerClientException("Could not create client")
            }

            val command by createForEachTest { VersionInfoCommand(versionInfo, PrintStream(outputStream), systemInfo, dockerClientFactory, gitClient, updateNotifier, logger) }
            val exitCode by runForEachTest { command.run() }

            it("prints version information") {
                assertThat(
                    outputStream.toString(),
                    equalTo(
                        """
                        |Batect version:    1.2.3
                        |Built:             THE BUILD DATE
                        |Built from commit: THE BUILD COMMIT (commit date: COMMIT DATE)
                        |JVM version:       THE JVM VERSION
                        |OS version:        THE OS VERSION
                        |Docker version:    (could not get Docker version information because DockerClientException was thrown: Could not create client)
                        |Git version:       (GIT VERSION INFO)
                        |
                        |For documentation and further information on Batect, visit https://github.com/batect/batect.
                        |
                        |
                        """.trimMargin().withPlatformSpecificLineSeparator()
                    )
                )
            }

            it("returns a zero exit code") {
                assertThat(exitCode, equalTo(0))
            }

            it("notifies the user of any updates") {
                verify(updateNotifier).run()
            }
        }
    }
})
