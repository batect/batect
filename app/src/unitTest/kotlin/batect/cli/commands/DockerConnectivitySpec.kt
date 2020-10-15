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

package batect.cli.commands

import batect.cli.CommandLineOptions
import batect.docker.api.BuilderVersion
import batect.docker.client.DockerConnectivityCheckResult
import batect.docker.client.DockerContainerType
import batect.docker.client.SystemInfoClient
import batect.ioc.DockerConfigurationKodeinFactory
import batect.primitives.Version
import batect.telemetry.DockerTelemetryCollector
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.runForEachTest
import batect.ui.Console
import batect.ui.text.Text
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import org.kodein.di.DI
import org.kodein.di.DirectDI
import org.kodein.di.bind
import org.kodein.di.instance
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

        val systemInfoClient by createForEachTest { mock<SystemInfoClient>() }
        val errorConsole by createForEachTest { mock<Console>() }
        val connectivity by createForEachTest { DockerConnectivity(dockerConfigurationKodeinFactory, systemInfoClient, errorConsole, commandLineOptions) }

        fun Suite.itRunsTheTaskWithBuilder(checkResult: DockerConnectivityCheckResult.Succeeded, expectedBuilderVersion: BuilderVersion) {
            var ranTask = false
            var kodeinSeenInTask: DirectDI? = null

            beforeEachTest {
                ranTask = false
                whenever(systemInfoClient.checkConnectivity()).doReturn(checkResult)
            }

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
                verifyZeroInteractions(errorConsole)
            }

            it("collects Docker environment telemetry") {
                verify(dockerTelemetryCollector).collectTelemetry(checkResult, expectedBuilderVersion)
            }

            it("creates the Kodein context with the expected builder version") {
                verify(dockerConfigurationKodeinFactory).create(any(), eq(expectedBuilderVersion))
            }
        }

        fun Suite.itDoesNotRunTheTaskAndFailsWithError(checkResult: DockerConnectivityCheckResult, expectedErrorMessage: String) {
            var ranTask = false

            beforeEachTest {
                ranTask = false
                whenever(systemInfoClient.checkConnectivity()).doReturn(checkResult)
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

        given("the connectivity check succeeds") {
            given("the user has not provided a builder preference") {
                beforeEachTest { whenever(commandLineOptions.enableBuildKit).doReturn(null) }

                val checkResult = DockerConnectivityCheckResult.Succeeded(containerType, Version(19, 3, 1), BuilderVersion.BuildKit, true)

                itRunsTheTaskWithBuilder(checkResult, BuilderVersion.Legacy)
            }

            given("the user has explicitly disabled BuildKit") {
                beforeEachTest { whenever(commandLineOptions.enableBuildKit).doReturn(false) }

                val checkResult = DockerConnectivityCheckResult.Succeeded(containerType, Version(19, 3, 1), BuilderVersion.BuildKit, true)

                itRunsTheTaskWithBuilder(checkResult, BuilderVersion.Legacy)
            }

            given("the user has requested BuildKit") {
                beforeEachTest { whenever(commandLineOptions.enableBuildKit).doReturn(true) }

                given("the user is running a version of the Docker daemon that supports BuildKit without experimental features being enabled") {
                    val dockerVersion = Version(18, 9, 0)
                    val checkResult = DockerConnectivityCheckResult.Succeeded(containerType, dockerVersion, BuilderVersion.Legacy, false)

                    itRunsTheTaskWithBuilder(checkResult, BuilderVersion.BuildKit)
                }

                given("the user is running a version of the Docker daemon that supports BuildKit but only if experimental features are enabled") {
                    val dockerVersion = Version(18, 6, 0)

                    given("experimental features are enabled") {
                        val checkResult = DockerConnectivityCheckResult.Succeeded(containerType, dockerVersion, BuilderVersion.Legacy, true)

                        itRunsTheTaskWithBuilder(checkResult, BuilderVersion.BuildKit)
                    }

                    given("experimental features are disabled") {
                        val checkResult = DockerConnectivityCheckResult.Succeeded(containerType, dockerVersion, BuilderVersion.Legacy, false)

                        itDoesNotRunTheTaskAndFailsWithError(checkResult, "BuildKit has been enabled with --enable-buildkit or the DOCKER_BUILDKIT environment variable, but the current version of Docker requires experimental features to be enabled to use BuildKit and experimental features are currently disabled.")
                    }
                }

                given("the user is running a version of the Docker daemon that does not support BuildKit") {
                    val dockerVersion = Version(17, 6, 0)
                    val checkResult = DockerConnectivityCheckResult.Succeeded(containerType, dockerVersion, BuilderVersion.BuildKit, true)

                    itDoesNotRunTheTaskAndFailsWithError(checkResult, "BuildKit has been enabled with --enable-buildkit or the DOCKER_BUILDKIT environment variable, but the current version of Docker does not support BuildKit, even with experimental features enabled.")
                }
            }
        }

        given("the connectivity check fails") {
            val checkResult = DockerConnectivityCheckResult.Failed("Something went wrong.")

            itDoesNotRunTheTaskAndFailsWithError(checkResult, "Docker is not installed, not running or not compatible with batect: Something went wrong.")
        }
    }
})
