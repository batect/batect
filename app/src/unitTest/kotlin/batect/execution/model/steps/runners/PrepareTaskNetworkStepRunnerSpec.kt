/*
    Copyright 2017-2022 Charles Korn.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package batect.execution.model.steps.runners

import batect.cli.CommandLineOptions
import batect.docker.DockerContainerType
import batect.docker.DockerResourceNameGenerator
import batect.dockerclient.DockerClient
import batect.dockerclient.NetworkCreationFailedException
import batect.dockerclient.NetworkReference
import batect.dockerclient.NetworkRetrievalFailedException
import batect.execution.model.events.CustomTaskNetworkCheckFailedEvent
import batect.execution.model.events.CustomTaskNetworkCheckedEvent
import batect.execution.model.events.TaskEventSink
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.events.TaskNetworkCreationFailedEvent
import batect.testutils.beforeEachTestSuspend
import batect.testutils.createForEachTest
import batect.testutils.createLoggerForEachTest
import batect.testutils.given
import batect.testutils.itSuspend
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object PrepareTaskNetworkStepRunnerSpec : Spek({
    describe("running a 'prepare task network' step") {
        val nameGenerator by createForEachTest {
            mock<DockerResourceNameGenerator> {
                on { generateNameFor("network") } doReturn "my-project-network"
            }
        }

        val dockerClient by createForEachTest { mock<DockerClient>() }
        val eventSink by createForEachTest { mock<TaskEventSink>() }
        val logger by createLoggerForEachTest()

        given("no network to use is provided on the command line") {
            val commandLineOptions = CommandLineOptions(existingNetworkToUse = null)

            given("creating the network succeeds") {
                val network = NetworkReference("some-network")

                beforeEachTestSuspend {
                    whenever(dockerClient.createNetwork(any(), any())).doReturn(network)
                }

                given("the active container type is Linux") {
                    val runner by createForEachTest { PrepareTaskNetworkStepRunner(nameGenerator, DockerContainerType.Linux, dockerClient, commandLineOptions, logger) }

                    beforeEachTest {
                        runner.run(eventSink)
                    }

                    it("emits a 'network created' event") {
                        verify(eventSink).postEvent(TaskNetworkCreatedEvent(NetworkReference("some-network")))
                    }

                    itSuspend("creates the network with the 'bridge' driver and name from the resource name generator") {
                        verify(dockerClient).createNetwork("my-project-network", "bridge")
                    }
                }

                given("the active container type is Windows") {
                    val runner by createForEachTest { PrepareTaskNetworkStepRunner(nameGenerator, DockerContainerType.Windows, dockerClient, commandLineOptions, logger) }

                    beforeEachTest {
                        runner.run(eventSink)
                    }

                    it("emits a 'network created' event") {
                        verify(eventSink).postEvent(TaskNetworkCreatedEvent(NetworkReference("some-network")))
                    }

                    itSuspend("creates the network with the 'nat' driver") {
                        verify(dockerClient).createNetwork("my-project-network", "nat")
                    }
                }
            }

            given("creating the network fails") {
                val runner by createForEachTest { PrepareTaskNetworkStepRunner(nameGenerator, DockerContainerType.Linux, dockerClient, commandLineOptions, logger) }

                beforeEachTestSuspend {
                    whenever(dockerClient.createNetwork(any(), any())).doThrow(NetworkCreationFailedException("Something went wrong."))

                    runner.run(eventSink)
                }

                it("emits a 'network creation failed' event") {
                    verify(eventSink).postEvent(TaskNetworkCreationFailedEvent("Something went wrong."))
                }
            }
        }

        given("a network to use is provided on the command line") {
            val commandLineOptions = CommandLineOptions(existingNetworkToUse = "my-network")
            val runner by createForEachTest { PrepareTaskNetworkStepRunner(nameGenerator, DockerContainerType.Linux, dockerClient, commandLineOptions, logger) }

            given("the network exists") {
                beforeEachTestSuspend {
                    whenever(dockerClient.getNetworkByNameOrID("my-network")).doReturn(NetworkReference("the-network-id"))

                    runner.run(eventSink)
                }

                it("emits a 'custom network checked' event") {
                    verify(eventSink).postEvent(CustomTaskNetworkCheckedEvent(NetworkReference("the-network-id")))
                }
            }

            given("the network does not exist") {
                beforeEachTestSuspend {
                    whenever(dockerClient.getNetworkByNameOrID("my-network")).doReturn(null)

                    runner.run(eventSink)
                }

                it("emits a 'custom network check failed' event") {
                    verify(eventSink).postEvent(CustomTaskNetworkCheckFailedEvent("my-network", "The network 'my-network' does not exist."))
                }
            }

            given("getting the network's details fails") {
                beforeEachTestSuspend {
                    whenever(dockerClient.getNetworkByNameOrID("my-network")).doThrow(NetworkRetrievalFailedException("Something went wrong"))

                    runner.run(eventSink)
                }

                it("emits a 'custom network check failed' event") {
                    verify(eventSink).postEvent(CustomTaskNetworkCheckFailedEvent("my-network", "Something went wrong"))
                }
            }
        }
    }
})
