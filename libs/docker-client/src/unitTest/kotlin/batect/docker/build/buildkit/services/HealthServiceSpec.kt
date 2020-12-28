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

package batect.docker.build.buildkit.services

import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import io.grpc.health.v1.HealthCheckRequest
import io.grpc.health.v1.HealthCheckResponse
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object HealthServiceSpec : Spek({
    describe("a gRPC health service") {
        val service by createForEachTest { HealthService() }

        describe("checking the health of the server") {
            val response by runForEachTest { service.Check(HealthCheckRequest()) }

            it("returns that the server is OK") {
                assertThat(response, equalTo(HealthCheckResponse(HealthCheckResponse.ServingStatus.SERVING)))
            }
        }
    }
})
