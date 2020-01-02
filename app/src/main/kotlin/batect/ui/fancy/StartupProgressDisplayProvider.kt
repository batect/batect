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

package batect.ui.fancy

import batect.execution.ContainerDependencyGraph
import batect.ui.ConsoleDimensions

class StartupProgressDisplayProvider(private val consoleDimensions: ConsoleDimensions) {
    fun createForDependencyGraph(graph: ContainerDependencyGraph): StartupProgressDisplay {
        val lines = graph.allNodes.map { ContainerStartupProgressLine(it.container, it.dependsOnContainers, it.isRootNode) }

        return StartupProgressDisplay(lines, consoleDimensions)
    }
}
