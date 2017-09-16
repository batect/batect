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

package batect.cli.commands

import batect.PrintStreamType
import batect.VersionInfo
import batect.cli.CommandLineParser
import batect.cli.Succeeded
import batect.docker.DockerClient
import batect.docker.DockerVersionInfoRetrievalFailedException
import batect.os.SystemInfo
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.bind
import com.github.salomonbrys.kodein.instance
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isA
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object VersionInfoCommandSpec : Spek({
    describe("a 'version info' command") {
        describe("command line interface") {
            val commandLine = VersionInfoCommandDefinition()

            val versionInfo = mock<VersionInfo>()
            val outputStream = mock<PrintStream>()
            val systemInfo = mock<SystemInfo>()
            val dockerClient = mock<DockerClient>()
            val commandLineParser = mock<CommandLineParser>()

            val kodein = Kodein {
                bind<VersionInfo>() with instance(versionInfo)
                bind<PrintStream>(PrintStreamType.Output) with instance(outputStream)
                bind<SystemInfo>() with instance(systemInfo)
                bind<DockerClient>() with instance(dockerClient)
                bind<CommandLineParser>() with instance(commandLineParser)
            }

            describe("when given no parameters") {
                val result = commandLine.parse(emptyList(), kodein)

                it("indicates that parsing succeeded") {
                    assertThat(result, isA<Succeeded>())
                }

                it("returns a command instance ready for use") {
                    assertThat((result as Succeeded).command,
                            equalTo<Command>(VersionInfoCommand(versionInfo, outputStream, systemInfo, dockerClient, commandLineParser)))
                }
            }
        }

        describe("when invoked") {
            val versionInfo = mock<VersionInfo> {
                on { version } doReturn "THE BATECT VERSION"
                on { buildDate } doReturn "THE BUILD DATE"
                on { gitCommitHash } doReturn "THE BUILD COMMIT"
                on { gitCommitDate } doReturn "COMMIT DATE"
            }

            val systemInfo = mock<SystemInfo> {
                on { jvmVersion } doReturn "THE JVM VERSION"
                on { osVersion } doReturn "THE OS VERSION"
            }

            val commandLineParser = mock<CommandLineParser> {
                on { helpBlurb } doReturn "For more information on batect, go to www.help.com."
            }

            val dockerClient = mock<DockerClient>()
            val outputStream = ByteArrayOutputStream()
            val command = VersionInfoCommand(versionInfo, PrintStream(outputStream), systemInfo, dockerClient, commandLineParser)

            beforeEachTest {
                reset(dockerClient)
                outputStream.reset()
            }

            on("the Docker client returning version info") {
                whenever(dockerClient.getDockerVersionInfo()).thenReturn("DOCKER VERSION INFO")

                val exitCode = command.run()
                val output = outputStream.toString()

                it("prints version information") {
                    assertThat(output, equalTo("""
                            |batect version:    THE BATECT VERSION
                            |Built:             THE BUILD DATE
                            |Built from commit: THE BUILD COMMIT (commit date: COMMIT DATE)
                            |JVM version:       THE JVM VERSION
                            |OS version:        THE OS VERSION
                            |Docker version:    DOCKER VERSION INFO
                            |
                            |For more information on batect, go to www.help.com.
                            |
                            |""".trimMargin()))
                }

                it("returns a zero exit code") {
                    assertThat(exitCode, equalTo(0))
                }
            }

            on("the Docker client throwing an exception when retrieving version information") {
                whenever(dockerClient.getDockerVersionInfo()).thenThrow(DockerVersionInfoRetrievalFailedException("Could not get Docker version info because XXX."))

                val exitCode = command.run()
                val output = outputStream.toString()

                it("prints version information with an explanation of why Docker version information is not shown") {
                    assertThat(output, equalTo("""
                            |batect version:    THE BATECT VERSION
                            |Built:             THE BUILD DATE
                            |Built from commit: THE BUILD COMMIT (commit date: COMMIT DATE)
                            |JVM version:       THE JVM VERSION
                            |OS version:        THE OS VERSION
                            |Docker version:    (Could not get Docker version info because XXX.)
                            |
                            |For more information on batect, go to www.help.com.
                            |
                            |""".trimMargin()))
                }

                it("returns a zero exit code") {
                    assertThat(exitCode, equalTo(0))
                }
            }
        }
    }
})
