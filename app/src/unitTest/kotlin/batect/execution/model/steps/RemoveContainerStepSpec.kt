/*
    Copyright 2017-2022 Charles Korn.

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
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.logRepresentationOf
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import org.araqnid.hamkrest.json.equivalentTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object RemoveContainerStepSpec : Spek({
    describe("a 'remove container' step") {
        val container = Container("the-container", imageSourceDoesNotMatter())
        val dockerContainer = DockerContainer("the-container-id", "the-container-name")
        val step = RemoveContainerStep(container, dockerContainer)

        on("attaching it to a log message") {
            it("returns a machine-readable representation of itself") {
                assertThat(
                    logRepresentationOf(step),
                    equivalentTo(
                        """
                        |{
                        |   "type": "${step::class.qualifiedName}",
                        |   "container": "the-container",
                        |   "dockerContainer": {"id": "the-container-id", "name": "the-container-name"}
                        |}
                        """.trimMargin()
                    )
                )
            }
        }
    }
})
