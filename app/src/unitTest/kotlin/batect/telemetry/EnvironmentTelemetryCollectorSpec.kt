/*
    Copyright 2017-2022 Charles Korn.

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

import batect.cli.CommandLineOptions
import batect.cli.commands.VersionInfoCommand
import batect.git.GitClient
import batect.git.GitVersionRetrievalResult
import batect.os.ConsoleInfo
import batect.os.HostEnvironmentVariables
import batect.os.SystemInfo
import batect.testutils.createForEachTest
import batect.testutils.given
import batect.testutils.osIndependentPath
import batect.ui.OutputStyle
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe
import java.lang.management.RuntimeMXBean
import java.util.Properties

object EnvironmentTelemetryCollectorSpec : Spek({
    describe("an environment telemetry collector") {
        val telemetrySessionBuilder by createForEachTest { mock<TelemetrySessionBuilder>() }
        val gitClient by createForEachTest { mock<GitClient>() }
        val systemProperties by createForEachTest { Properties() }
        val ciEnvironmentDetector by createForEachTest {
            mock<CIEnvironmentDetector> {
                on { detect() } doReturn CIDetectionResult(false, null)
            }
        }

        val consoleInfo by createForEachTest {
            mock<ConsoleInfo> {
                on { stdinIsTTY } doReturn false
                on { stdoutIsTTY } doReturn true
                on { supportsInteractivity } doReturn true
            }
        }

        val systemInfo by createForEachTest {
            mock<SystemInfo> {
                on { osArchitecture } doReturn "x86_64"
                on { osName } doReturn "My Cool OS"
                on { osVersion } doReturn "2020.08"
                on { osDetails } doReturn "Ubuntu Linux 20.04"
            }
        }

        val runtimeMXBean by createForEachTest {
            mock<RuntimeMXBean> {
                on { startTime } doReturn 1597138787193 // 2020-08-11T09:39:47.193Z
            }
        }

        val commandType = VersionInfoCommand::class

        beforeEachTest {
            systemProperties.setProperty("java.vendor", "JVMs R Us")
            systemProperties.setProperty("java.version", "2020.1")
            systemProperties.setProperty("java.vm.name", "64-Bit Server VM")
        }

        fun Suite.createEnvironmentCollector(hostEnvironmentVariables: HostEnvironmentVariables, commandLineOptions: CommandLineOptions = CommandLineOptions()) =
            createForEachTest { EnvironmentTelemetryCollector(telemetrySessionBuilder, hostEnvironmentVariables, gitClient, consoleInfo, commandLineOptions, ciEnvironmentDetector, systemInfo, runtimeMXBean, systemProperties) }

        given("the SHELL environment variable is set") {
            val shell = "/usr/local/bin/my-awesome-shell"

            given("the TERM environment variable is set") {
                val term = "xterm-9000color"

                given("the BATECT_WRAPPER_DID_DOWNLOAD environment variable is set to 'true'") {
                    val didDownload = "true"
                    val hostEnvironmentVariables = HostEnvironmentVariables("SHELL" to shell, "TERM" to term, "BATECT_WRAPPER_DID_DOWNLOAD" to didDownload)

                    given("the command line options have their default values") {
                        val commandLineOptions = CommandLineOptions()
                        val environmentCollector by createEnvironmentCollector(hostEnvironmentVariables, commandLineOptions)

                        given("the Git version is available") {
                            beforeEachTest {
                                whenever(gitClient.version).doReturn(GitVersionRetrievalResult.Succeeded("1.2.3"))
                            }

                            given("a CI system is not detected") {
                                beforeEachTest {
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
                                    verify(telemetrySessionBuilder).addAttribute("osDetails", "Ubuntu Linux 20.04")
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

                                it("reports the time the JVM started, in UTC") {
                                    verify(telemetrySessionBuilder).addAttribute("jvmStartTime", "2020-08-11T09:39:47.193Z")
                                }

                                it("reports whether stdin is a TTY") {
                                    verify(telemetrySessionBuilder).addAttribute("stdinIsTTY", false)
                                }

                                it("reports whether stdout is a TTY") {
                                    verify(telemetrySessionBuilder).addAttribute("stdoutIsTTY", true)
                                }

                                it("reports whether interactivity is supported") {
                                    verify(telemetrySessionBuilder).addAttribute("consoleSupportsInteractivity", true)
                                }

                                it("reports that a custom configuration file name is not being used") {
                                    verify(telemetrySessionBuilder).addAttribute("usingNonDefaultConfigurationFileName", false)
                                }

                                it("reports that a config variables file is not being used") {
                                    verify(telemetrySessionBuilder).addAttribute("usingConfigVariablesFile", false)
                                }

                                it("reports the requested output style") {
                                    verify(telemetrySessionBuilder).addAttribute("requestedOutputStyle", null as String?)
                                }

                                it("reports that color output has not been disabled") {
                                    verify(telemetrySessionBuilder).addAttribute("colorOutputDisabled", false)
                                }

                                it("reports that update notifications have not been disabled") {
                                    verify(telemetrySessionBuilder).addAttribute("updateNotificationsDisabled", false)
                                }

                                it("reports that wrapper cache cleanup has not been disabled") {
                                    verify(telemetrySessionBuilder).addAttribute("wrapperCacheCleanupDisabled", false)
                                }

                                it("reports that cleanup after success has not been disabled") {
                                    verify(telemetrySessionBuilder).addAttribute("cleanupAfterSuccessDisabled", false)
                                }

                                it("reports that cleanup after failure has not been disabled") {
                                    verify(telemetrySessionBuilder).addAttribute("cleanupAfterFailureDisabled", false)
                                }

                                it("reports that proxy environment variable propagation has not been disabled") {
                                    verify(telemetrySessionBuilder).addAttribute("proxyEnvironmentVariablePropagationDisabled", false)
                                }

                                it("reports the number of additional task command arguments") {
                                    verify(telemetrySessionBuilder).addAttribute("additionalTaskCommandArgumentCount", 0)
                                }

                                it("reports the number of command line config variable overrides") {
                                    verify(telemetrySessionBuilder).addAttribute("commandLineConfigVariableOverrideCount", 0)
                                }

                                it("reports the number of command line image overrides") {
                                    verify(telemetrySessionBuilder).addAttribute("commandLineImageOverrideCount", 0)
                                }

                                it("reports that TLS is not being used for the connection to Docker") {
                                    verify(telemetrySessionBuilder).addAttribute("usingTLSForDockerConnection", false)
                                }

                                it("reports that TLS is not being verified for the connection to Docker") {
                                    verify(telemetrySessionBuilder).addAttribute("verifyingTLSForDockerConnection", false)
                                }

                                it("reports that an existing Docker network is not being used") {
                                    verify(telemetrySessionBuilder).addAttribute("usingExistingDockerNetwork", false)
                                }

                                it("reports that prerequisites are not being skipped") {
                                    verify(telemetrySessionBuilder).addAttribute("skippingPrerequisites", false)
                                }

                                it("reports that no maximum level of parallelism is set") {
                                    verify(telemetrySessionBuilder).addAttribute("maximumLevelOfParallelism", null as Int?)
                                }

                                it("reports that no CI system was detected") {
                                    verify(telemetrySessionBuilder).addAttribute("suspectRunningOnCI", false)
                                    verify(telemetrySessionBuilder).addAttribute("suspectedCISystem", null as String?)
                                }
                            }

                            given("a CI system is detected") {
                                beforeEachTest {
                                    whenever(ciEnvironmentDetector.detect()).doReturn(CIDetectionResult(true, "My CI System"))

                                    environmentCollector.collect(commandType)
                                }

                                it("reports that a CI system was detected") {
                                    verify(telemetrySessionBuilder).addAttribute("suspectRunningOnCI", true)
                                }

                                it("reports the CI system name") {
                                    verify(telemetrySessionBuilder).addAttribute("suspectedCISystem", "My CI System")
                                }
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

                    given("the command line options do not have their default values") {
                        val commandLineOptions = CommandLineOptions(
                            configurationFileName = osIndependentPath("not-batect.yml"),
                            configVariablesSourceFile = osIndependentPath("some-variables.yml"),
                            requestedOutputStyle = OutputStyle.Fancy,
                            disableColorOutput = true,
                            disableUpdateNotification = true,
                            disableWrapperCacheCleanup = true,
                            disableCleanupAfterSuccess = true,
                            disableCleanupAfterFailure = true,
                            dontPropagateProxyEnvironmentVariables = true,
                            additionalTaskCommandArguments = listOf("some", "args"),
                            configVariableOverrides = mapOf("var" to "value", "other-var" to "other value"),
                            imageOverrides = mapOf("container-1" to "image-1"),
                            dockerUseTLS = true,
                            dockerVerifyTLS = true,
                            existingNetworkToUse = "some-network",
                            skipPrerequisites = true,
                            maximumLevelOfParallelism = 3
                        )

                        val environmentCollector by createEnvironmentCollector(hostEnvironmentVariables, commandLineOptions)

                        beforeEachTest { environmentCollector.collect(commandType) }

                        it("reports that a custom configuration file name is being used") {
                            verify(telemetrySessionBuilder).addAttribute("usingNonDefaultConfigurationFileName", true)
                        }

                        it("reports that a config variables file is being used") {
                            verify(telemetrySessionBuilder).addAttribute("usingConfigVariablesFile", true)
                        }

                        it("reports the requested output style") {
                            verify(telemetrySessionBuilder).addAttribute("requestedOutputStyle", "fancy")
                        }

                        it("reports that color output has been disabled") {
                            verify(telemetrySessionBuilder).addAttribute("colorOutputDisabled", true)
                        }

                        it("reports that update notifications have been disabled") {
                            verify(telemetrySessionBuilder).addAttribute("updateNotificationsDisabled", true)
                        }

                        it("reports that wrapper cache cleanup has been disabled") {
                            verify(telemetrySessionBuilder).addAttribute("wrapperCacheCleanupDisabled", true)
                        }

                        it("reports that cleanup after success has been disabled") {
                            verify(telemetrySessionBuilder).addAttribute("cleanupAfterSuccessDisabled", true)
                        }

                        it("reports that cleanup after failure has been disabled") {
                            verify(telemetrySessionBuilder).addAttribute("cleanupAfterFailureDisabled", true)
                        }

                        it("reports that proxy environment variable propagation has been disabled") {
                            verify(telemetrySessionBuilder).addAttribute("proxyEnvironmentVariablePropagationDisabled", true)
                        }

                        it("reports the number of additional task command arguments") {
                            verify(telemetrySessionBuilder).addAttribute("additionalTaskCommandArgumentCount", 2)
                        }

                        it("reports the number of command line config variable overrides") {
                            verify(telemetrySessionBuilder).addAttribute("commandLineConfigVariableOverrideCount", 2)
                        }

                        it("reports the number of command line image overrides") {
                            verify(telemetrySessionBuilder).addAttribute("commandLineImageOverrideCount", 1)
                        }

                        it("reports that TLS is being used for the connection to Docker") {
                            verify(telemetrySessionBuilder).addAttribute("usingTLSForDockerConnection", true)
                        }

                        it("reports that TLS is being verified for the connection to Docker") {
                            verify(telemetrySessionBuilder).addAttribute("verifyingTLSForDockerConnection", true)
                        }

                        it("reports that an existing Docker network is being used") {
                            verify(telemetrySessionBuilder).addAttribute("usingExistingDockerNetwork", true)
                        }

                        it("reports that prerequisites are being skipped") {
                            verify(telemetrySessionBuilder).addAttribute("skippingPrerequisites", true)
                        }

                        it("reports the configured maximum level of parallelism") {
                            verify(telemetrySessionBuilder).addAttribute("maximumLevelOfParallelism", 3)
                        }
                    }
                }

                given("the BATECT_WRAPPER_DID_DOWNLOAD environment variable is set to 'false'") {
                    val didDownload = "false"
                    val hostEnvironmentVariables = HostEnvironmentVariables("SHELL" to shell, "TERM" to term, "BATECT_WRAPPER_DID_DOWNLOAD" to didDownload)
                    val environmentCollector by createEnvironmentCollector(hostEnvironmentVariables)
                    beforeEachTest { environmentCollector.collect(commandType) }

                    it("reports that the wrapper did not download the JAR for this invocation") {
                        verify(telemetrySessionBuilder).addAttribute("wrapperDidDownload", false)
                    }
                }

                given("the BATECT_WRAPPER_DID_DOWNLOAD environment variable is not set to 'true' or 'false'") {
                    val didDownload = "blah"
                    val hostEnvironmentVariables = HostEnvironmentVariables("SHELL" to shell, "TERM" to term, "BATECT_WRAPPER_DID_DOWNLOAD" to didDownload)
                    val environmentCollector by createEnvironmentCollector(hostEnvironmentVariables)
                    beforeEachTest { environmentCollector.collect(commandType) }

                    it("reports the download status as unknown") {
                        verify(telemetrySessionBuilder).addNullAttribute("wrapperDidDownload")
                    }
                }

                given("the BATECT_WRAPPER_DID_DOWNLOAD environment variable is not set") {
                    val hostEnvironmentVariables = HostEnvironmentVariables("SHELL" to shell, "TERM" to term)
                    val environmentCollector by createEnvironmentCollector(hostEnvironmentVariables)
                    beforeEachTest { environmentCollector.collect(commandType) }

                    it("reports the download status as unknown") {
                        verify(telemetrySessionBuilder).addNullAttribute("wrapperDidDownload")
                    }
                }
            }

            given("the TERM environment variable is not set") {
                val hostEnvironmentVariables = HostEnvironmentVariables("SHELL" to shell)
                val environmentCollector by createEnvironmentCollector(hostEnvironmentVariables)

                beforeEachTest { environmentCollector.collect(commandType) }

                it("reports the user's terminal as unknown") {
                    verify(telemetrySessionBuilder).addAttribute("terminal", null as String?)
                }
            }
        }

        given("the SHELL environment variable is not set") {
            val hostEnvironmentVariables = HostEnvironmentVariables("TERM" to "something")
            val environmentCollector by createEnvironmentCollector(hostEnvironmentVariables)

            beforeEachTest { environmentCollector.collect(commandType) }

            it("reports the user's shell as unknown") {
                verify(telemetrySessionBuilder).addAttribute("shell", null as String?)
            }
        }
    }
})
