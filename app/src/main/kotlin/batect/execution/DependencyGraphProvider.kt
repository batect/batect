/*
   Copyright 2017-2018 Charles Korn.

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

package batect.execution

import batect.config.Configuration
import batect.config.Task
import batect.logging.Logger
import batect.utils.mapToSet

class DependencyGraphProvider(private val containerCommandResolver: ContainerCommandResolver, private val logger: Logger) {
    fun createGraph(config: Configuration, task: Task): DependencyGraph {
        val graph = DependencyGraph(config, task, containerCommandResolver)

        logger.info {
            val dependenciesList = graph.allNodes
                .associate { it.container.name to dependencyNames(it) }

            message("Dependency graph for task created.")
            data("task", task)
            data("dependencies", dependenciesList)
        }

        return graph
    }

    private fun dependencyNames(node: DependencyGraphNode): Set<String> = node.dependsOnContainers.mapToSet { it.name }
}
