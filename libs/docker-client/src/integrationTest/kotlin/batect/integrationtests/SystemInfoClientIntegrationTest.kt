/*
    Copyright 2017-2021 Charles Korn.

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

package batect.integrationtests

import batect.docker.client.DockerConnectivityCheckResult
import batect.docker.client.DockerVersionInfoRetrievalResult
import batect.testutils.createForGroup
import batect.testutils.runBeforeGroup
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isA
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SystemInfoClientIntegrationTest : Spek({
    describe("a Docker system information client") {
        val client by createForGroup { createClient() }

        describe("checking if Docker is available") {
            val result by runBeforeGroup { client.systemInfo.checkConnectivity() }

            it("returns that Docker is available") {
                assertThat(result, isA<DockerConnectivityCheckResult.Succeeded>())
            }
        }

        describe("getting Docker version info") {
            val versionInfoRetrievalResult by runBeforeGroup { client.systemInfo.getDockerVersionInfo() }

            it("succeeds") {
                assertThat(versionInfoRetrievalResult, isA<DockerVersionInfoRetrievalResult.Succeeded>())
            }
        }
    }
})
