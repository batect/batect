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
import batect.docker.DockerImage
import batect.docker.DockerNetwork
import batect.execution.ContainerRuntimeConfiguration
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockitokotlin2.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object CreateContainerStepSpec : Spek({
    describe("a 'create container' step") {
        val container = Container("the-container", imageSourceDoesNotMatter())
        val config = mock<ContainerRuntimeConfiguration>()
        val image = DockerImage("the-image")
        val network = DockerNetwork("the-network")

        val step = CreateContainerStep(container, config, image, network)

        on("toString()") {
            it("returns a human-readable representation of itself") {
                assertThat(
                    step.toString(),
                    equalTo("CreateContainerStep(container: 'the-container', config: $config, image: 'the-image', network: 'the-network')")
                )
            }
        }
    }
})
