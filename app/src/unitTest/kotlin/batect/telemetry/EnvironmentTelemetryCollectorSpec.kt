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

package batect.telemetry

import batect.cli.CommandLineOptions
import batect.cli.commands.VersionInfoCommand
import batect.git.GitClient
import batect.git.GitVersionRetrievalResult
import batect.os.ConsoleInfo
import batect.os.HostEnvironmentVariables
import batect.os.SystemInfo
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.osIndependentPath
import batect.ui.OutputStyle
import com.natpryce.hamkrest.assertion.assertThat
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe
import java.lang.management.RuntimeMXBean
import java.util.Properties

object EnvironmentTelemetryCollectorSpec : Spek({
    describe("an environment telemetry collector") {
        val telemetryCaptor by createForEachTest { TestTelemetryCaptor() }
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
            createForEachTest { EnvironmentTelemetryCollector(telemetryCaptor, hostEnvironmentVariables, gitClient, consoleInfo, commandLineOptions, ciEnvironmentDetector, systemInfo, runtimeMXBean, systemProperties) }

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
                                    assertThat(telemetryCaptor.allAttributes["shell"], equalTo(JsonPrimitive("my-awesome-shell")))
                                }

                                it("reports the user's terminal") {
                                    assertThat(telemetryCaptor.allAttributes["terminal"], equalTo(JsonPrimitive("xterm-9000color")))
                                }

                                it("reports the Git version") {
                                    assertThat(telemetryCaptor.allAttributes["gitVersion"], equalTo(JsonPrimitive("1.2.3")))
                                }

                                it("reports details of the operating system") {
                                    assertThat(telemetryCaptor.allAttributes["osName"], equalTo(JsonPrimitive("My Cool OS")))
                                    assertThat(telemetryCaptor.allAttributes["osVersion"], equalTo(JsonPrimitive("2020.08")))
                                    assertThat(telemetryCaptor.allAttributes["osArchitecture"], equalTo(JsonPrimitive("x86_64")))
                                    assertThat(telemetryCaptor.allAttributes["osDetails"], equalTo(JsonPrimitive("Ubuntu Linux 20.04")))
                                }

                                it("reports details of the JVM") {
                                    assertThat(telemetryCaptor.allAttributes["jvmVendor"], equalTo(JsonPrimitive("JVMs R Us")))
                                    assertThat(telemetryCaptor.allAttributes["jvmName"], equalTo(JsonPrimitive("64-Bit Server VM")))
                                    assertThat(telemetryCaptor.allAttributes["jvmVersion"], equalTo(JsonPrimitive("2020.1")))
                                }

                                it("reports the type of command being run") {
                                    assertThat(telemetryCaptor.allAttributes["commandType"], equalTo(JsonPrimitive("VersionInfoCommand")))
                                }

                                it("reports that the wrapper downloaded the JAR for this invocation") {
                                    assertThat(telemetryCaptor.allAttributes["wrapperDidDownload"], equalTo(JsonPrimitive(true)))
                                }

                                it("reports the time the JVM started, in UTC") {
                                    assertThat(telemetryCaptor.allAttributes["jvmStartTime"], equalTo(JsonPrimitive("2020-08-11T09:39:47.193Z")))
                                }

                                it("reports whether stdin is a TTY") {
                                    assertThat(telemetryCaptor.allAttributes["stdinIsTTY"], equalTo(JsonPrimitive(false)))
                                }

                                it("reports whether stdout is a TTY") {
                                    assertThat(telemetryCaptor.allAttributes["stdoutIsTTY"], equalTo(JsonPrimitive(true)))
                                }

                                it("reports whether interactivity is supported") {
                                    assertThat(telemetryCaptor.allAttributes["consoleSupportsInteractivity"], equalTo(JsonPrimitive(true)))
                                }

                                it("reports that a custom configuration file name is not being used") {
                                    assertThat(telemetryCaptor.allAttributes["usingNonDefaultConfigurationFileName"], equalTo(JsonPrimitive(false)))
                                }

                                it("reports that a config variables file is not being used") {
                                    assertThat(telemetryCaptor.allAttributes["usingConfigVariablesFile"], equalTo(JsonPrimitive(false)))
                                }

                                it("reports the requested output style") {
                                    assertThat(telemetryCaptor.allAttributes["requestedOutputStyle"], equalTo(JsonNull))
                                }

                                it("reports that color output has not been disabled") {
                                    assertThat(telemetryCaptor.allAttributes["colorOutputDisabled"], equalTo(JsonPrimitive(false)))
                                }

                                it("reports that update notifications have not been disabled") {
                                    assertThat(telemetryCaptor.allAttributes["updateNotificationsDisabled"], equalTo(JsonPrimitive(false)))
                                }

                                it("reports that wrapper cache cleanup has not been disabled") {
                                    assertThat(telemetryCaptor.allAttributes["wrapperCacheCleanupDisabled"], equalTo(JsonPrimitive(false)))
                                }

                                it("reports that cleanup after success has not been disabled") {
                                    assertThat(telemetryCaptor.allAttributes["cleanupAfterSuccessDisabled"], equalTo(JsonPrimitive(false)))
                                }

                                it("reports that cleanup after failure has not been disabled") {
                                    assertThat(telemetryCaptor.allAttributes["cleanupAfterFailureDisabled"], equalTo(JsonPrimitive(false)))
                                }

                                it("reports that proxy environment variable propagation has not been disabled") {
                                    assertThat(telemetryCaptor.allAttributes["proxyEnvironmentVariablePropagationDisabled"], equalTo(JsonPrimitive(false)))
                                }

                                it("reports the number of additional task command arguments") {
                                    assertThat(telemetryCaptor.allAttributes["additionalTaskCommandArgumentCount"], equalTo(JsonPrimitive(0)))
                                }

                                it("reports the number of command line config variable overrides") {
                                    assertThat(telemetryCaptor.allAttributes["commandLineConfigVariableOverrideCount"], equalTo(JsonPrimitive(0)))
                                }

                                it("reports the number of command line image overrides") {
                                    assertThat(telemetryCaptor.allAttributes["commandLineImageOverrideCount"], equalTo(JsonPrimitive(0)))
                                }

                                it("reports that TLS is not being used for the connection to Docker") {
                                    assertThat(telemetryCaptor.allAttributes["usingTLSForDockerConnection"], equalTo(JsonPrimitive(false)))
                                }

                                it("reports that TLS is not being verified for the connection to Docker") {
                                    assertThat(telemetryCaptor.allAttributes["verifyingTLSForDockerConnection"], equalTo(JsonPrimitive(false)))
                                }

                                it("reports that an existing Docker network is not being used") {
                                    assertThat(telemetryCaptor.allAttributes["usingExistingDockerNetwork"], equalTo(JsonPrimitive(false)))
                                }

                                it("reports that prerequisites are not being skipped") {
                                    assertThat(telemetryCaptor.allAttributes["skippingPrerequisites"], equalTo(JsonPrimitive(false)))
                                }

                                it("reports that no maximum level of parallelism is set") {
                                    assertThat(telemetryCaptor.allAttributes["maximumLevelOfParallelism"], equalTo(JsonNull))
                                }

                                it("reports that no CI system was detected") {
                                    assertThat(telemetryCaptor.allAttributes["suspectRunningOnCI"], equalTo(JsonPrimitive(false)))
                                    assertThat(telemetryCaptor.allAttributes["suspectedCISystem"], equalTo(JsonNull))
                                }
                            }

                            given("a CI system is detected") {
                                beforeEachTest {
                                    whenever(ciEnvironmentDetector.detect()).doReturn(CIDetectionResult(true, "My CI System"))

                                    environmentCollector.collect(commandType)
                                }

                                it("reports that a CI system was detected") {
                                    assertThat(telemetryCaptor.allAttributes["suspectRunningOnCI"], equalTo(JsonPrimitive(true)))
                                }

                                it("reports the CI system name") {
                                    assertThat(telemetryCaptor.allAttributes["suspectedCISystem"], equalTo(JsonPrimitive("My CI System")))
                                }
                            }
                        }

                        given("the Git version is not available") {
                            beforeEachTest {
                                whenever(gitClient.version).doReturn(GitVersionRetrievalResult.Failed("Something went wrong."))

                                environmentCollector.collect(commandType)
                            }

                            it("reports the Git version as unknown") {
                                assertThat(telemetryCaptor.allAttributes["gitVersion"], equalTo(JsonNull))
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
                            assertThat(telemetryCaptor.allAttributes["usingNonDefaultConfigurationFileName"], equalTo(JsonPrimitive(true)))
                        }

                        it("reports that a config variables file is being used") {
                            assertThat(telemetryCaptor.allAttributes["usingConfigVariablesFile"], equalTo(JsonPrimitive(true)))
                        }

                        it("reports the requested output style") {
                            assertThat(telemetryCaptor.allAttributes["requestedOutputStyle"], equalTo(JsonPrimitive("fancy")))
                        }

                        it("reports that color output has been disabled") {
                            assertThat(telemetryCaptor.allAttributes["colorOutputDisabled"], equalTo(JsonPrimitive(true)))
                        }

                        it("reports that update notifications have been disabled") {
                            assertThat(telemetryCaptor.allAttributes["updateNotificationsDisabled"], equalTo(JsonPrimitive(true)))
                        }

                        it("reports that wrapper cache cleanup has been disabled") {
                            assertThat(telemetryCaptor.allAttributes["wrapperCacheCleanupDisabled"], equalTo(JsonPrimitive(true)))
                        }

                        it("reports that cleanup after success has been disabled") {
                            assertThat(telemetryCaptor.allAttributes["cleanupAfterSuccessDisabled"], equalTo(JsonPrimitive(true)))
                        }

                        it("reports that cleanup after failure has been disabled") {
                            assertThat(telemetryCaptor.allAttributes["cleanupAfterFailureDisabled"], equalTo(JsonPrimitive(true)))
                        }

                        it("reports that proxy environment variable propagation has been disabled") {
                            assertThat(telemetryCaptor.allAttributes["proxyEnvironmentVariablePropagationDisabled"], equalTo(JsonPrimitive(true)))
                        }

                        it("reports the number of additional task command arguments") {
                            assertThat(telemetryCaptor.allAttributes["additionalTaskCommandArgumentCount"], equalTo(JsonPrimitive(2)))
                        }

                        it("reports the number of command line config variable overrides") {
                            assertThat(telemetryCaptor.allAttributes["commandLineConfigVariableOverrideCount"], equalTo(JsonPrimitive(2)))
                        }

                        it("reports the number of command line image overrides") {
                            assertThat(telemetryCaptor.allAttributes["commandLineImageOverrideCount"], equalTo(JsonPrimitive(1)))
                        }

                        it("reports that TLS is being used for the connection to Docker") {
                            assertThat(telemetryCaptor.allAttributes["usingTLSForDockerConnection"], equalTo(JsonPrimitive(true)))
                        }

                        it("reports that TLS is being verified for the connection to Docker") {
                            assertThat(telemetryCaptor.allAttributes["verifyingTLSForDockerConnection"], equalTo(JsonPrimitive(true)))
                        }

                        it("reports that an existing Docker network is being used") {
                            assertThat(telemetryCaptor.allAttributes["usingExistingDockerNetwork"], equalTo(JsonPrimitive(true)))
                        }

                        it("reports that prerequisites are being skipped") {
                            assertThat(telemetryCaptor.allAttributes["skippingPrerequisites"], equalTo(JsonPrimitive(true)))
                        }

                        it("reports the configured maximum level of parallelism") {
                            assertThat(telemetryCaptor.allAttributes["maximumLevelOfParallelism"], equalTo(JsonPrimitive(3)))
                        }
                    }
                }

                given("the BATECT_WRAPPER_DID_DOWNLOAD environment variable is set to 'false'") {
                    val didDownload = "false"
                    val hostEnvironmentVariables = HostEnvironmentVariables("SHELL" to shell, "TERM" to term, "BATECT_WRAPPER_DID_DOWNLOAD" to didDownload)
                    val environmentCollector by createEnvironmentCollector(hostEnvironmentVariables)
                    beforeEachTest { environmentCollector.collect(commandType) }

                    it("reports that the wrapper did not download the JAR for this invocation") {
                        assertThat(telemetryCaptor.allAttributes["wrapperDidDownload"], equalTo(JsonPrimitive(false)))
                    }
                }

                given("the BATECT_WRAPPER_DID_DOWNLOAD environment variable is not set to 'true' or 'false'") {
                    val didDownload = "blah"
                    val hostEnvironmentVariables = HostEnvironmentVariables("SHELL" to shell, "TERM" to term, "BATECT_WRAPPER_DID_DOWNLOAD" to didDownload)
                    val environmentCollector by createEnvironmentCollector(hostEnvironmentVariables)
                    beforeEachTest { environmentCollector.collect(commandType) }

                    it("reports the download status as unknown") {
                        assertThat(telemetryCaptor.allAttributes["wrapperDidDownload"], equalTo(JsonNull))
                    }
                }

                given("the BATECT_WRAPPER_DID_DOWNLOAD environment variable is not set") {
                    val hostEnvironmentVariables = HostEnvironmentVariables("SHELL" to shell, "TERM" to term)
                    val environmentCollector by createEnvironmentCollector(hostEnvironmentVariables)
                    beforeEachTest { environmentCollector.collect(commandType) }

                    it("reports the download status as unknown") {
                        assertThat(telemetryCaptor.allAttributes["wrapperDidDownload"], equalTo(JsonNull))
                    }
                }
            }

            given("the TERM environment variable is not set") {
                val hostEnvironmentVariables = HostEnvironmentVariables("SHELL" to shell)
                val environmentCollector by createEnvironmentCollector(hostEnvironmentVariables)

                beforeEachTest { environmentCollector.collect(commandType) }

                it("reports the user's terminal as unknown") {
                    assertThat(telemetryCaptor.allAttributes["terminal"], equalTo(JsonNull))
                }
            }
        }

        given("the SHELL environment variable is not set") {
            val hostEnvironmentVariables = HostEnvironmentVariables("TERM" to "something")
            val environmentCollector by createEnvironmentCollector(hostEnvironmentVariables)

            beforeEachTest { environmentCollector.collect(commandType) }

            it("reports the user's shell as unknown") {
                assertThat(telemetryCaptor.allAttributes["shell"], equalTo(JsonNull))
            }
        }
    }
})
