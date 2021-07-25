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

package batect.ui.fancy

import batect.config.Container
import batect.execution.ContainerDependencyGraph
import batect.execution.ContainerDependencyGraphNode
import batect.os.ConsoleDimensions
import batect.testutils.imageSourceDoesNotMatter
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object StartupProgressDisplayProviderSpec : Spek({
    describe("a startup progress display provider") {
        val consoleDimensions = mock<ConsoleDimensions>()
        val provider = StartupProgressDisplayProvider(consoleDimensions)

        fun createNodeFor(container: Container, dependencies: Set<Container>, isTaskContainer: Boolean): ContainerDependencyGraphNode {
            return mock {
                on { this.container } doReturn container
                on { dependsOnContainers } doReturn dependencies
                on { isRootNode } doReturn isTaskContainer
            }
        }

        on("creating a progress display for a dependency graph") {
            val container1 = Container("container-1", imageSourceDoesNotMatter())
            val container2 = Container("container-2", imageSourceDoesNotMatter())

            val container1Dependencies = setOf(container2)
            val container2Dependencies = emptySet<Container>()

            val node1 = createNodeFor(container1, container1Dependencies, true)
            val node2 = createNodeFor(container2, container2Dependencies, false)

            val graph = mock<ContainerDependencyGraph> {
                on { allNodes } doReturn setOf(node1, node2)
            }

            val display = provider.createForDependencyGraph(graph)

            it("returns progress lines for each node in the graph with their dependencies") {
                assertThat(
                    display.containerLines.toSet(),
                    equalTo(
                        setOf(
                            ContainerStartupProgressLine(container1, container1Dependencies, true),
                            ContainerStartupProgressLine(container2, container2Dependencies, false)
                        )
                    )
                )
            }
        }
    }
})
