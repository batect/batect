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

package batect.integrationtests.endtoend

import batect.docker.DockerContainer
import batect.docker.DockerHealthCheckResult
import batect.docker.client.HealthStatus
import batect.integrationtests.build
import batect.integrationtests.createClient
import batect.integrationtests.creationRequestForContainer
import batect.integrationtests.runContainer
import batect.integrationtests.testImagesDirectory
import batect.integrationtests.withContainer
import batect.integrationtests.withNetwork
import batect.primitives.CancellationContext
import batect.testutils.createForGroup
import batect.testutils.given
import batect.testutils.runBeforeGroup
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object HealthCheckEndToEndIntegrationTest : Spek({
    describe("waiting for a container to become healthy") {
        val client by createForGroup { createClient() }
        val image by runBeforeGroup { client.build(testImagesDirectory.resolve("basic-image"), "batect-integration-tests-image") }

        data class Result(val healthStatus: HealthStatus, val lastHealthCheckResult: DockerHealthCheckResult)

        fun runContainerAndWaitForHealthCheck(container: DockerContainer): Result {
            return client.runContainer(container) {
                val healthStatus = client.containers.waitForHealthStatus(container, CancellationContext())
                val lastHealthCheckResult = client.containers.getLastHealthCheckResult(container)
                Result(healthStatus, lastHealthCheckResult).also {
                    client.containers.stop(container)
                }
            }
        }

        given("a container that immediately reports as healthy") {
            val result by runBeforeGroup {
                client.withNetwork { network ->
                    client.withContainer(creationRequestForContainer(image, network, ContainerCommands.waitIndefinitely)) { container ->
                        runContainerAndWaitForHealthCheck(container)
                    }
                }
            }

            it("reports that the container became healthy") {
                assertThat(result.healthStatus, equalTo(HealthStatus.BecameHealthy))
            }

            it("reports the result of the last health check") {
                assertThat(result.lastHealthCheckResult, equalTo(DockerHealthCheckResult(0, "Hello from the healthcheck")))
            }
        }
    }
})
