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

package batect.execution.model.steps

import batect.config.Container
import batect.docker.DockerContainer
import batect.execution.ContainerRuntimeConfiguration
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RunContainerSetupCommandsStepSpec : Spek({
    describe("a 'run container setup commands' step") {
        val container = Container("the-container", imageSourceDoesNotMatter())
        val config = ContainerRuntimeConfiguration(null, null, null, emptyMap(), emptySet())
        val allContainersInNetwork = setOf(container)
        val dockerContainer = DockerContainer("the-container-id")
        val step = RunContainerSetupCommandsStep(container, config, allContainersInNetwork, dockerContainer)

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assertThat(step.toString(), equalTo("RunContainerSetupCommandsStep(container: 'the-container', config: $config, all containers in network: ['the-container'], Docker container: 'the-container-id')"))
            }
        }
    }
})
