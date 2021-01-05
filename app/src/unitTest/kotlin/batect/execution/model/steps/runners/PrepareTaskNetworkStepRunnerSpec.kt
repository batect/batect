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

package batect.execution.model.steps.runners

import batect.cli.CommandLineOptions
import batect.docker.DockerNetwork
import batect.docker.DockerResourceNameGenerator
import batect.docker.api.NetworkCreationFailedException
import batect.docker.api.NetworkDoesNotExistException
import batect.docker.api.NetworkInspectionFailedException
import batect.docker.client.DockerContainerType
import batect.docker.client.NetworksClient
import batect.execution.model.events.CustomTaskNetworkCheckFailedEvent
import batect.execution.model.events.CustomTaskNetworkCheckedEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.events.TaskNetworkCreationFailedEvent
import batect.testutils.createForEachTest
import batect.testutils.given
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object PrepareTaskNetworkStepRunnerSpec : Spek({
    describe("running a 'prepare task network' step") {
        val nameGenerator by createForEachTest {
            mock<DockerResourceNameGenerator> {
                on { generateNameFor("network") } doReturn "my-project-network"
            }
        }

        val networksClient by createForEachTest { mock<NetworksClient>() }
        val eventSink by createForEachTest { mock<TaskEventSink>() }

        given("no network to use is provided on the command line") {
            val commandLineOptions = CommandLineOptions(existingNetworkToUse = null)

            given("creating the network succeeds") {
                val network = DockerNetwork("some-network")

                beforeEachTest {
                    whenever(networksClient.create(any(), any())).doReturn(network)
                }

                given("the active container type is Linux") {
                    val runner by createForEachTest { PrepareTaskNetworkStepRunner(nameGenerator, DockerContainerType.Linux, networksClient, commandLineOptions) }

                    beforeEachTest {
                        runner.run(eventSink)
                    }

                    it("emits a 'network created' event") {
                        verify(eventSink).postEvent(TaskNetworkCreatedEvent(network))
                    }

                    it("creates the network with the 'bridge' driver and name from the resource name generator") {
                        verify(networksClient).create("my-project-network", "bridge")
                    }
                }

                given("the active container type is Windows") {
                    val runner by createForEachTest { PrepareTaskNetworkStepRunner(nameGenerator, DockerContainerType.Windows, networksClient, commandLineOptions) }

                    beforeEachTest {
                        runner.run(eventSink)
                    }

                    it("emits a 'network created' event") {
                        verify(eventSink).postEvent(TaskNetworkCreatedEvent(network))
                    }

                    it("creates the network with the 'nat' driver") {
                        verify(networksClient).create("my-project-network", "nat")
                    }
                }
            }

            given("creating the network fails") {
                val runner by createForEachTest { PrepareTaskNetworkStepRunner(nameGenerator, DockerContainerType.Linux, networksClient, commandLineOptions) }

                beforeEachTest {
                    whenever(networksClient.create(any(), any())).doThrow(NetworkCreationFailedException("Something went wrong."))

                    runner.run(eventSink)
                }

                it("emits a 'network creation failed' event") {
                    verify(eventSink).postEvent(TaskNetworkCreationFailedEvent("Something went wrong."))
                }
            }
        }

        given("a network to use is provided on the command line") {
            val commandLineOptions = CommandLineOptions(existingNetworkToUse = "my-network")
            val runner by createForEachTest { PrepareTaskNetworkStepRunner(nameGenerator, DockerContainerType.Linux, networksClient, commandLineOptions) }

            given("the network exists") {
                beforeEachTest {
                    whenever(networksClient.getByNameOrId("my-network")).doReturn(DockerNetwork("the-network-id"))

                    runner.run(eventSink)
                }

                it("emits a 'custom network checked' event") {
                    verify(eventSink).postEvent(CustomTaskNetworkCheckedEvent(DockerNetwork("the-network-id")))
                }
            }

            given("the network does not exist") {
                beforeEachTest {
                    whenever(networksClient.getByNameOrId("my-network")).doThrow(NetworkDoesNotExistException("my-network"))

                    runner.run(eventSink)
                }

                it("emits a 'custom network check failed' event") {
                    verify(eventSink).postEvent(CustomTaskNetworkCheckFailedEvent("my-network", "The network 'my-network' does not exist."))
                }
            }

            given("getting the network's details fails") {
                beforeEachTest {
                    whenever(networksClient.getByNameOrId("my-network")).doThrow(NetworkInspectionFailedException("my-network", "Something went wrong"))

                    runner.run(eventSink)
                }

                it("emits a 'custom network check failed' event") {
                    verify(eventSink).postEvent(CustomTaskNetworkCheckFailedEvent("my-network", "Getting details of network 'my-network' failed: Something went wrong"))
                }
            }
        }
    }
})
