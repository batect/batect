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

package batect.telemetry

import batect.cli.commands.VersionInfoCommand
import batect.git.GitClient
import batect.git.GitVersionRetrievalResult
import batect.os.HostEnvironmentVariables
import batect.testutils.createForEachTest
import batect.testutils.given
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.util.Properties
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TelemetryEnvironmentCollectorSpec : Spek({
    describe("a telemetry environment collector") {
        val telemetrySessionBuilder by createForEachTest { mock<TelemetrySessionBuilder>() }
        val gitClient by createForEachTest { mock<GitClient>() }
        val systemProperties by createForEachTest { Properties() }
        val commandType = VersionInfoCommand::class

        beforeEachTest {
            systemProperties.setProperty("os.arch", "x86_64")
            systemProperties.setProperty("os.name", "My Cool OS")
            systemProperties.setProperty("os.version", "2020.08")
            systemProperties.setProperty("java.vendor", "JVMs R Us")
            systemProperties.setProperty("java.version", "2020.1")
            systemProperties.setProperty("java.vm.name", "64-Bit Server VM")
        }

        given("the SHELL environment variable is set") {
            val shell = "/usr/local/bin/my-awesome-shell"

            given("the TERM environment variable is set") {
                val term = "xterm-9000color"

                given("the BATECT_WRAPPER_DID_DOWNLOAD environment variable is set to 'true'") {
                    val didDownload = "true"
                    val hostEnvironmentVariables by createForEachTest { HostEnvironmentVariables("SHELL" to shell, "TERM" to term, "BATECT_WRAPPER_DID_DOWNLOAD" to didDownload) }
                    val environmentCollector by createForEachTest { TelemetryEnvironmentCollector(telemetrySessionBuilder, hostEnvironmentVariables, gitClient, systemProperties) }

                    given("the Git version is available") {
                        beforeEachTest {
                            whenever(gitClient.version).doReturn(GitVersionRetrievalResult.Succeeded("1.2.3"))

                            environmentCollector.collect(commandType)
                        }

                        it("reports the user's shell, taking only the last segment of the path") {
                            verify(telemetrySessionBuilder).addAttribute("shell", "my-awesome-shell")
                        }

                        it("reports the user's terminal") {
                            verify(telemetrySessionBuilder).addAttribute("terminal", "xterm-9000color")
                        }

                        it("reports the Git version") {
                            verify(telemetrySessionBuilder).addAttribute("gitVersion", "1.2.3")
                        }

                        it("reports details of the operating system") {
                            verify(telemetrySessionBuilder).addAttribute("osName", "My Cool OS")
                            verify(telemetrySessionBuilder).addAttribute("osVersion", "2020.08")
                            verify(telemetrySessionBuilder).addAttribute("osArchitecture", "x86_64")
                        }

                        it("reports details of the JVM") {
                            verify(telemetrySessionBuilder).addAttribute("jvmVendor", "JVMs R Us")
                            verify(telemetrySessionBuilder).addAttribute("jvmName", "64-Bit Server VM")
                            verify(telemetrySessionBuilder).addAttribute("jvmVersion", "2020.1")
                        }

                        it("reports the type of command being run") {
                            verify(telemetrySessionBuilder).addAttribute("commandType", "VersionInfoCommand")
                        }

                        it("reports that the wrapper downloaded the JAR for this invocation") {
                            verify(telemetrySessionBuilder).addAttribute("wrapperDidDownload", true)
                        }
                    }

                    given("the Git version is not available") {
                        beforeEachTest {
                            whenever(gitClient.version).doReturn(GitVersionRetrievalResult.Failed("Something went wrong."))

                            environmentCollector.collect(commandType)
                        }

                        it("reports the Git version as unknown") {
                            verify(telemetrySessionBuilder).addNullAttribute("gitVersion")
                        }
                    }
                }

                given("the BATECT_WRAPPER_DID_DOWNLOAD environment variable is set to 'false'") {
                    val didDownload = "false"
                    val hostEnvironmentVariables by createForEachTest { HostEnvironmentVariables("SHELL" to shell, "TERM" to term, "BATECT_WRAPPER_DID_DOWNLOAD" to didDownload) }
                    val environmentCollector by createForEachTest { TelemetryEnvironmentCollector(telemetrySessionBuilder, hostEnvironmentVariables, gitClient, systemProperties) }
                    beforeEachTest { environmentCollector.collect(commandType) }

                    it("reports that the wrapper did not download the JAR for this invocation") {
                        verify(telemetrySessionBuilder).addAttribute("wrapperDidDownload", false)
                    }
                }

                given("the BATECT_WRAPPER_DID_DOWNLOAD environment variable is not set to 'true' or 'false'") {
                    val didDownload = "blah"
                    val hostEnvironmentVariables by createForEachTest { HostEnvironmentVariables("SHELL" to shell, "TERM" to term, "BATECT_WRAPPER_DID_DOWNLOAD" to didDownload) }
                    val environmentCollector by createForEachTest { TelemetryEnvironmentCollector(telemetrySessionBuilder, hostEnvironmentVariables, gitClient, systemProperties) }
                    beforeEachTest { environmentCollector.collect(commandType) }

                    it("reports the download status as unknown") {
                        verify(telemetrySessionBuilder).addNullAttribute("wrapperDidDownload")
                    }
                }

                given("the BATECT_WRAPPER_DID_DOWNLOAD environment variable is not set") {
                    val hostEnvironmentVariables by createForEachTest { HostEnvironmentVariables("SHELL" to shell, "TERM" to term) }
                    val environmentCollector by createForEachTest { TelemetryEnvironmentCollector(telemetrySessionBuilder, hostEnvironmentVariables, gitClient, systemProperties) }
                    beforeEachTest { environmentCollector.collect(commandType) }

                    it("reports the download status as unknown") {
                        verify(telemetrySessionBuilder).addNullAttribute("wrapperDidDownload")
                    }
                }
            }

            given("the TERM environment variable is not set") {
                val hostEnvironmentVariables by createForEachTest { HostEnvironmentVariables("SHELL" to shell) }
                val environmentCollector by createForEachTest { TelemetryEnvironmentCollector(telemetrySessionBuilder, hostEnvironmentVariables, gitClient, systemProperties) }

                beforeEachTest { environmentCollector.collect(commandType) }

                it("reports the user's terminal as unknown") {
                    verify(telemetrySessionBuilder).addAttribute("terminal", null as String?)
                }
            }
        }

        given("the SHELL environment variable is not set") {
            val hostEnvironmentVariables by createForEachTest { HostEnvironmentVariables("TERM" to "something") }
            val environmentCollector by createForEachTest { TelemetryEnvironmentCollector(telemetrySessionBuilder, hostEnvironmentVariables, gitClient, systemProperties) }

            beforeEachTest { environmentCollector.collect(commandType) }

            it("reports the user's shell as unknown") {
                verify(telemetrySessionBuilder).addAttribute("shell", null as String?)
            }
        }
    }
})
