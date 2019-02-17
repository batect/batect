/*
   Copyright 2017-2019 Charles Korn.

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

package batect.execution.model.steps

import batect.config.Container
import batect.config.LiteralValue
import batect.config.PortMapping
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.os.Command
import batect.testutils.imageSourceDoesNotMatter
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object CreateContainerStepSpec : Spek({
    describe("a 'create container' step") {
        val container = Container("the-container", imageSourceDoesNotMatter())
        val otherContainer = Container("the-other-container", imageSourceDoesNotMatter())
        val workingDirectory = "some-dir"
        val additionalEnvironmentVariables = mapOf("SOME_VAR" to LiteralValue("some value"))
        val additionalPortMappings = setOf(PortMapping(123, 456))
        val image = DockerImage("the-image")
        val network = DockerNetwork("the-network")
        val allContainersInNetwork = setOf(container, otherContainer)

        given("there is an explicit command") {
            val command = Command.parse("the-command some-arg")
            val step = CreateContainerStep(container, command, workingDirectory, additionalEnvironmentVariables, additionalPortMappings, allContainersInNetwork, image, network)

            on("toString()") {
                it("returns a human-readable representation of itself") {
                    assertThat(step.toString(),
                        equalTo("CreateContainerStep(container: 'the-container', command: [the-command, some-arg], working directory: some-dir, additional environment variables: [SOME_VAR=LiteralValue(value: 'some value')], additional port mappings: [123:456], all containers in network: ['the-container', 'the-other-container'], image: 'the-image', network: 'the-network')"))
                }
            }
        }

        given("there is no explicit command") {
            val command = null
            val step = CreateContainerStep(container, command, workingDirectory, additionalEnvironmentVariables, additionalPortMappings, allContainersInNetwork, image, network)

            on("toString()") {
                it("returns a human-readable representation of itself") {
                    assertThat(step.toString(),
                        equalTo("CreateContainerStep(container: 'the-container', command: null, working directory: some-dir, additional environment variables: [SOME_VAR=LiteralValue(value: 'some value')], additional port mappings: [123:456], all containers in network: ['the-container', 'the-other-container'], image: 'the-image', network: 'the-network')"))
                }
            }
        }
    }
})
