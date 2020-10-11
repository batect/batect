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
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import org.kodein.di.DI
import org.kodein.di.DirectDI
import org.kodein.di.bind
import org.kodein.di.instance
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DockerConnectivitySpec : Spek({
    describe("a Docker connectivity check") {
        val containerType = DockerContainerType.Linux
        val dockerTelemetryCollector by createForEachTest { mock<DockerTelemetryCollector>() }
        val kodeinFromFactory by createForEachTest {
            DI.direct {
                bind<String>() with instance("Something from the base Kodein")
                bind<DockerTelemetryCollector>() with instance(dockerTelemetryCollector)
            }
        }

        val dockerConfigurationKodeinFactory by createForEachTest {
            mock<DockerConfigurationKodeinFactory> {
                on { create(containerType) } doReturn kodeinFromFactory
            }
        }

        val systemInfoClient by createForEachTest { mock<SystemInfoClient>() }
        val errorConsole by createForEachTest { mock<Console>() }
        val connectivity by createForEachTest { DockerConnectivity(dockerConfigurationKodeinFactory, systemInfoClient, errorConsole) }

        given("the check succeeds") {
            var ranTask = false
            var kodeinSeenInTask: DirectDI? = null

            beforeEachTest {
                ranTask = false

                whenever(systemInfoClient.checkConnectivity()).doReturn(DockerConnectivityCheckResult.Succeeded(containerType, Version(19, 3, 1)))
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
                verify(dockerTelemetryCollector).collectTelemetry(DockerConnectivityCheckResult.Succeeded(containerType, Version(19, 3, 1)))
            }
        }

        given("the check fails") {
            var ranTask = false

            beforeEachTest {
                ranTask = false

                whenever(systemInfoClient.checkConnectivity()).doReturn(DockerConnectivityCheckResult.Failed("Something went wrong."))
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
                verify(errorConsole).println(Text.red("Docker is not installed, not running or not compatible with batect: Something went wrong."))
            }

            it("returns a non-zero exit code") {
                assertThat(exitCode, !equalTo(0))
            }
        }
    }
})
