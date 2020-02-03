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

package batect.execution.model.steps.runners

import batect.docker.DockerNetwork
import batect.docker.api.NetworkCreationFailedException
import batect.docker.client.DockerContainerType
import batect.docker.client.DockerNetworksClient
import batect.execution.model.events.TaskEventSink
import batect.execution.model.events.TaskNetworkCreatedEvent
import batect.execution.model.events.TaskNetworkCreationFailedEvent
import batect.execution.model.steps.CreateTaskNetworkStep
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

object CreateTaskNetworkStepRunnerSpec : Spek({
    describe("running a 'create task network' step") {
        val networksClient by createForEachTest { mock<DockerNetworksClient>() }
        val eventSink by createForEachTest { mock<TaskEventSink>() }

        val runner by createForEachTest { CreateTaskNetworkStepRunner(networksClient) }

        given("creating the network succeeds") {
            val network = DockerNetwork("some-network")

            beforeEachTest {
                whenever(networksClient.create(any())).doReturn(network)
            }

            given("the active container type is Linux") {
                val step = CreateTaskNetworkStep(DockerContainerType.Linux)

                beforeEachTest {
                    runner.run(step, eventSink)
                }

                it("emits a 'network created' event") {
                    verify(eventSink).postEvent(TaskNetworkCreatedEvent(network))
                }

                it("creates the network with the 'bridge' driver") {
                    verify(networksClient).create("bridge")
                }
            }

            given("the active container type is Windows") {
                val step = CreateTaskNetworkStep(DockerContainerType.Windows)

                beforeEachTest {
                    runner.run(step, eventSink)
                }

                it("emits a 'network created' event") {
                    verify(eventSink).postEvent(TaskNetworkCreatedEvent(network))
                }

                it("creates the network with the 'nat' driver") {
                    verify(networksClient).create("nat")
                }
            }
        }

        given("creating the network fails") {
            val step = CreateTaskNetworkStep(DockerContainerType.Linux)

            beforeEachTest {
                whenever(networksClient.create(any())).doThrow(NetworkCreationFailedException("Something went wrong."))

                runner.run(step, eventSink)
            }

            it("emits a 'network creation failed' event") {
                verify(eventSink).postEvent(TaskNetworkCreationFailedEvent("Something went wrong."))
            }
        }
    }
})
