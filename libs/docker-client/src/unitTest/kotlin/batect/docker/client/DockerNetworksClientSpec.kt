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

package batect.docker.client

import batect.docker.DockerNetwork
import batect.docker.api.NetworksAPI
import batect.testutils.createForEachTest
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.on
import batect.testutils.runForEachTest
import com.natpryce.hamkrest.assertion.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DockerNetworksClientSpec : Spek({
    describe("a Docker networks client") {
        val api by createForEachTest { mock<NetworksAPI>() }
        val client by createForEachTest { DockerNetworksClient(api) }

        on("creating a network") {
            beforeEachTest { whenever(api.create(any())).doReturn(DockerNetwork("the-network-id")) }

            val result by runForEachTest { client.create("the-driver") }

            it("creates the network") {
                verify(api).create("the-driver")
            }

            it("returns the ID of the created network") {
                assertThat(result.id, equalTo("the-network-id"))
            }
        }

        describe("deleting a network") {
            given("an existing network") {
                val network = DockerNetwork("abc123")

                on("deleting that network") {
                    beforeEachTest { client.delete(network) }

                    it("sends a request to the Docker daemon to delete the network") {
                        verify(api).delete(network)
                    }
                }
            }
        }

        on("getting a network by name or ID") {
            beforeEachTest { whenever(api.getByNameOrId("the-network")).doReturn(DockerNetwork("the-network-id")) }

            val result by runForEachTest { client.getByNameOrId("the-network") }

            it("returns the ID of the network") {
                assertThat(result.id, equalTo("the-network-id"))
            }
        }
    }
})
