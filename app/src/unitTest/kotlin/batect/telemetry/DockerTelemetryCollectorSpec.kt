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

import batect.docker.ConnectionType
import batect.docker.DockerHttpConfig
import batect.docker.client.DockerConnectivityCheckResult
import batect.docker.client.DockerContainerType
import batect.dockerclient.BuilderVersion
import batect.execution.CacheManager
import batect.execution.CacheType
import batect.primitives.Version
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import com.natpryce.hamkrest.assertion.assertThat
import kotlinx.serialization.json.JsonPrimitive
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DockerTelemetryCollectorSpec : Spek({
    describe("a Docker telemetry collector") {
        val dockerHttpConfig by createForEachTest {
            mock<DockerHttpConfig> {
                on { connectionType } doReturn ConnectionType.TCP
            }
        }

        val cacheManager by createForEachTest {
            mock<CacheManager> {
                on { cacheType } doReturn CacheType.Directory
            }
        }

        val telemetryCaptor by createForEachTest { TestTelemetryCaptor() }
        val dockerTelemetryCollector by createForEachTest { DockerTelemetryCollector(dockerHttpConfig, cacheManager, telemetryCaptor) }

        describe("when collecting telemetry") {
            val checkResult = DockerConnectivityCheckResult.Succeeded(DockerContainerType.Linux, Version(19, 3, 1), batect.docker.api.BuilderVersion.BuildKit, false)
            beforeEachTest { dockerTelemetryCollector.collectTelemetry(checkResult, BuilderVersion.Legacy) }

            it("adds the Docker version as an attribute on the telemetry session") {
                assertThat(telemetryCaptor.allAttributes["dockerVersion"], equalTo(JsonPrimitive("19.3.1")))
            }

            it("adds the Docker container type as an attribute on the telemetry session") {
                assertThat(telemetryCaptor.allAttributes["dockerContainerType"], equalTo(JsonPrimitive("Linux")))
            }

            it("adds the daemon's preferred builder version as an attribute on the telemetry session") {
                assertThat(telemetryCaptor.allAttributes["dockerDaemonPreferredBuilderVersion"], equalTo(JsonPrimitive("BuildKit")))
            }

            it("adds the in-use builder version as an attribute on the telemetry session") {
                assertThat(telemetryCaptor.allAttributes["dockerBuilderVersionInUse"], equalTo(JsonPrimitive("Legacy")))
            }

            it("adds the daemon's experimental status as an attribute on the telemetry session") {
                assertThat(telemetryCaptor.allAttributes["dockerDaemonExperimentalFeaturesEnabled"], equalTo(JsonPrimitive(false)))
            }

            it("adds the Docker connection type as an attribute on the telemetry session") {
                assertThat(telemetryCaptor.allAttributes["dockerConnectionType"], equalTo(JsonPrimitive("TCP")))
            }

            it("adds the cache type as an attribute on the telemetry session") {
                assertThat(telemetryCaptor.allAttributes["cacheType"], equalTo(JsonPrimitive("Directory")))
            }
        }
    }
})
