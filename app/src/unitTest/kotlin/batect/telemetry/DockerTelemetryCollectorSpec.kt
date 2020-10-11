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

import batect.docker.ConnectionType
import batect.docker.DockerHttpConfig
import batect.docker.api.BuilderVersion
import batect.docker.client.DockerConnectivityCheckResult
import batect.docker.client.DockerContainerType
import batect.execution.CacheManager
import batect.execution.CacheType
import batect.primitives.Version
import batect.testutils.createForEachTest
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
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

        val telemetrySessionBuilder by createForEachTest { mock<TelemetrySessionBuilder>() }
        val dockerTelemetryCollector by createForEachTest { DockerTelemetryCollector(dockerHttpConfig, cacheManager, telemetrySessionBuilder) }

        describe("when collecting telemetry") {
            beforeEachTest { dockerTelemetryCollector.collectTelemetry(DockerConnectivityCheckResult.Succeeded(DockerContainerType.Linux, Version(19, 3, 1), BuilderVersion.BuildKit)) }

            it("adds the Docker version as an attribute on the telemetry session") {
                verify(telemetrySessionBuilder).addAttribute("dockerVersion", "19.3.1")
            }

            it("adds the Docker container type as an attribute on the telemetry session") {
                verify(telemetrySessionBuilder).addAttribute("dockerContainerType", "Linux")
            }

            it("adds the daemon's preferred builder version as an attribute on the telemetry session") {
                verify(telemetrySessionBuilder).addAttribute("dockerDaemonPreferredBuilderVersion", "BuildKit")
            }

            it("adds the Docker connection type as an attribute on the telemetry session") {
                verify(telemetrySessionBuilder).addAttribute("dockerConnectionType", "TCP")
            }

            it("adds the cache type as an attribute on the telemetry session") {
                verify(telemetrySessionBuilder).addAttribute("cacheType", "Directory")
            }
        }
    }
})
