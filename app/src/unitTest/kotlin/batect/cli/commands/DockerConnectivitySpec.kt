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

package batect.cli.commands

import batect.cli.CommandLineOptions
import batect.docker.DockerConnectivityCheckResult
import batect.docker.DockerContainerType
import batect.dockerclient.BuilderVersion
import batect.dockerclient.DaemonVersionInformation
import batect.dockerclient.DockerClient
import batect.dockerclient.DockerClientException
import batect.dockerclient.PingResponse
import batect.ioc.DockerConfigurationKodeinFactory
import batect.primitives.Version
import batect.telemetry.DockerTelemetryCollector
import batect.telemetry.TestTelemetryCaptor
import batect.testutils.beforeEachTestSuspend
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.runForEachTest
import batect.ui.Console
import batect.ui.text.Text
import com.natpryce.hamkrest.assertion.assertThat
import kotlinx.coroutines.runBlocking
import org.kodein.di.DI
import org.kodein.di.DirectDI
import org.kodein.di.bind
import org.kodein.di.instance
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe

object DockerConnectivitySpec : Spek({
    describe("a Docker connectivity check") {
        val containerType = DockerContainerType.Linux
        val dockerTelemetryCollector by createForEachTest { mock<DockerTelemetryCollector>() }
        val commandLineOptions by createForEachTest { mock<CommandLineOptions>() }
        val kodeinFromFactory by createForEachTest {
            DI.direct {
                bind<String>() with instance("Something from the base Kodein")
                bind<DockerTelemetryCollector>() with instance(dockerTelemetryCollector)
            }
        }

        val dockerConfigurationKodeinFactory by createForEachTest {
            mock<DockerConfigurationKodeinFactory> {
                on { create(eq(containerType), any()) } doReturn kodeinFromFactory
            }
        }

        val dockerClient by createForEachTest { mock<DockerClient>() }
        val errorConsole by createForEachTest { mock<Console>() }
        val telemetryCaptor by createForEachTest { TestTelemetryCaptor() }
        val logger by createLoggerForEachTest()
        val connectivity by createForEachTest {
            DockerConnectivity(
                dockerConfigurationKodeinFactory,
                dockerClient,
                errorConsole,
                commandLineOptions,
                telemetryCaptor,
                logger
            )
        }

        fun setUpScenario(version: String, builderVersion: BuilderVersion, experimental: Boolean): DockerConnectivityCheckResult.Succeeded {
            runBlocking {
                whenever(dockerClient.ping()).doReturn(PingResponse("", "linux", experimental, builderVersion))
                whenever(dockerClient.getDaemonVersionInformation()).doReturn(DaemonVersionInformation(version, "1.37", "", "", "linux", "", experimental))
            }

            return DockerConnectivityCheckResult.Succeeded(DockerContainerType.Linux, Version.parse(version), builderVersion, experimental)
        }

        fun Suite.itRunsTheTaskWithBuilder(version: String, builderVersion: BuilderVersion, experimental: Boolean, expectedBuilderVersion: BuilderVersion) {
            var ranTask = false
            var kodeinSeenInTask: DirectDI? = null
            val checkResult by createForEachTest { setUpScenario(version, builderVersion, experimental) }

            beforeEachTest { ranTask = false }

            val exitCode by runForEachTest {
                connectivity.checkAndRun { kodein ->
                    ranTask = true
                    kodeinSeenInTask = kodein
                    123
                }
            }

            it("runs the task") {
                assertThat(ranTask, equalTo(true))
            }

            it("passes the created Kodein context to the task") {
                assertThat(kodeinSeenInTask, equalTo(kodeinFromFactory))
            }

            it("returns the exit code from the task") {
                assertThat(exitCode, equalTo(123))
            }

            it("does not print anything to the console") {
                verifyNoInteractions(errorConsole)
            }

            it("collects Docker environment telemetry") {
                verify(dockerTelemetryCollector).collectTelemetry(checkResult, expectedBuilderVersion)
            }

            it("creates the Kodein context with the expected builder version") {
                verify(dockerConfigurationKodeinFactory).create(any(), eq(expectedBuilderVersion))
            }
        }

        fun Suite.itDoesNotRunTheTaskAndFailsWithError(expectedErrorMessage: String) {
            var ranTask = false

            beforeEachTest {
                ranTask = false
            }

            val exitCode by runForEachTest {
                connectivity.checkAndRun {
                    ranTask = true
                    0
                }
            }

            it("does not run the task") {
                assertThat(ranTask, equalTo(false))
            }

            it("prints a message to the output") {
                verify(errorConsole).println(Text.red(expectedErrorMessage))
            }

            it("returns a non-zero exit code") {
                assertThat(exitCode, !equalTo(0))
            }
        }

        given("the Docker daemon is compatible with Batect") {
            given("the user has not provided a builder preference") {
                beforeEachTest { whenever(commandLineOptions.enableBuildKit).doReturn(null) }

                given("the builder reports that it prefers the legacy builder") {
                    itRunsTheTaskWithBuilder("19.3.1", BuilderVersion.Legacy, true, BuilderVersion.Legacy)
                }

                given("the builder reports that it prefers BuildKit") {
                    itRunsTheTaskWithBuilder("19.3.1", BuilderVersion.BuildKit, true, BuilderVersion.BuildKit)
                }
            }

            given("the user has explicitly disabled BuildKit") {
                beforeEachTest { whenever(commandLineOptions.enableBuildKit).doReturn(false) }

                itRunsTheTaskWithBuilder("19.3.1", BuilderVersion.BuildKit, true, BuilderVersion.Legacy)
            }

            given("the user has requested BuildKit") {
                beforeEachTest { whenever(commandLineOptions.enableBuildKit).doReturn(true) }

                given("the user is running a version of the Docker daemon that supports BuildKit without experimental features being enabled") {
                    itRunsTheTaskWithBuilder("18.9.0", BuilderVersion.Legacy, false, BuilderVersion.BuildKit)
                }

                given("the user is running a version of the Docker daemon that supports BuildKit but only if experimental features are enabled") {
                    val dockerVersion = "18.6.0"

                    given("experimental features are enabled") {
                        itRunsTheTaskWithBuilder(dockerVersion, BuilderVersion.Legacy, true, BuilderVersion.BuildKit)
                    }

                    given("experimental features are disabled") {
                        beforeEachTest {
                            setUpScenario(dockerVersion, BuilderVersion.Legacy, false)
                        }

                        itDoesNotRunTheTaskAndFailsWithError("BuildKit has been enabled with --enable-buildkit or the DOCKER_BUILDKIT environment variable, but the current version of Docker requires experimental features to be enabled to use BuildKit and experimental features are currently disabled.")
                    }
                }

                given("the user is running a version of the Docker daemon that does not support BuildKit") {
                    beforeEachTest { setUpScenario("17.6.0", BuilderVersion.BuildKit, true) }

                    itDoesNotRunTheTaskAndFailsWithError("BuildKit has been enabled with --enable-buildkit or the DOCKER_BUILDKIT environment variable, but the current version of Docker does not support BuildKit, even with experimental features enabled.")
                }
            }
        }

        given("the Docker daemon is not compatible with Batect") {
            beforeEachTestSuspend {
                whenever(dockerClient.getDaemonVersionInformation())
                    .doReturn(DaemonVersionInformation("1.2.3", "1.36", "", "", "", "", false))
            }

            itDoesNotRunTheTaskAndFailsWithError("Docker is not installed, not running or not compatible with Batect: Batect requires Docker 18.03.1 or later, but version 1.2.3 is installed.")
        }

        given("pinging the Docker daemon fails") {
            beforeEachTestSuspend {
                whenever(dockerClient.ping()).doThrow(DockerClientException("Something went wrong."))
            }

            itDoesNotRunTheTaskAndFailsWithError("Docker is not installed, not running or not compatible with Batect: Something went wrong.")
        }

        given("getting version information from the Docker daemon fails") {
            beforeEachTestSuspend {
                whenever(dockerClient.getDaemonVersionInformation()).doThrow(DockerClientException("Something went wrong."))
            }

            itDoesNotRunTheTaskAndFailsWithError("Docker is not installed, not running or not compatible with Batect: Something went wrong.")
        }
    }
})
