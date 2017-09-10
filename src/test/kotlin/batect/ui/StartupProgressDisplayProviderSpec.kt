/*
   Copyright 2017 Charles Korn.

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

package batect.ui

import batect.config.Container
import batect.model.DependencyGraph
import batect.model.DependencyGraphNode
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object StartupProgressDisplayProviderSpec : Spek({
    describe("a startup progress display provider") {
        val provider = StartupProgressDisplayProvider()

        fun createNodeFor(container: Container): DependencyGraphNode {
            return mock<DependencyGraphNode> {
                on { this.container } doReturn container
            }
        }

        on("creating a progress display for a dependency graph") {
            val container1 = Container("container-1", "/container-1-build-dir")
            val container2 = Container("container-2", "/container-2-build-dir")

            val node1 = createNodeFor(container1)
            val node2 = createNodeFor(container2)

            val graph = mock<DependencyGraph> {
                on { allNodes } doReturn setOf(node1, node2)
            }

            val display = provider.createForDependencyGraph(graph)

            it("returns a display with progress lines for each node in the graph") {
                assertThat(display.containerLines.map { it.container }.toSet(),
                        equalTo(setOf(container1, container2)))
            }
        }
    }
})
