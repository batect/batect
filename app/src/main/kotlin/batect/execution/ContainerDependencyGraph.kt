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
import batect.config.Container
import batect.config.EnvironmentVariableExpression
import batect.config.PortMapping
import batect.config.Task
import batect.os.Command

data class ContainerDependencyGraph(val config: Configuration, val task: Task, val containerCommandResolver: ContainerCommandResolver) {
    private val nodesMap = createNodes()

    val taskContainerNode: ContainerDependencyGraphNode by lazy { nodeFor(config.containers[task.runConfiguration.container]!!) }
    val allNodes = nodesMap.values

    fun nodeFor(container: Container): ContainerDependencyGraphNode {
        val node = nodesMap[container]

        if (node == null) {
            throw IllegalArgumentException("Container '${container.name}' is not part of this dependency graph.")
        }

        return node
    }

    private fun createNodes(): Map<Container, ContainerDependencyGraphNode> {
        if (task.dependsOnContainers.contains(task.runConfiguration.container)) {
            throw DependencyResolutionFailedException("The task '${task.name}' cannot start the container '${task.runConfiguration.container}' and also run it.")
        }

        val taskContainer = findContainer(task.runConfiguration.container, "task '${task.name}'")
        val nodesCreated = mutableMapOf<Container, ContainerDependencyGraphNode>()
        val taskDependencies = findContainers(task.dependsOnContainers, "task '${task.name}'") + findContainers(taskContainer.dependencies, "container '${taskContainer.name}'")
        getOrCreateNode(taskContainer, taskDependencies, true, nodesCreated, emptyList())

        return nodesCreated
    }

    private fun getOrCreateNode(container: Container, nodesAlreadyCreated: MutableMap<Container, ContainerDependencyGraphNode>, path: List<Container>): ContainerDependencyGraphNode {
        val dependencies = findContainers(container.dependencies, "container '${container.name}'")

        return getOrCreateNode(container, dependencies, false, nodesAlreadyCreated, path)
    }

    private fun getOrCreateNode(container: Container, dependencies: Set<Container>, isRootNode: Boolean, nodesAlreadyCreated: MutableMap<Container, ContainerDependencyGraphNode>, path: List<Container>): ContainerDependencyGraphNode {
        return nodesAlreadyCreated.getOrPut(container) {
            if (dependencies.contains(container)) {
                throw DependencyResolutionFailedException("The container '${container.name}' cannot depend on itself.")
            }

            val newPath = path + container

            if (path.contains(container)) {
                throw dependencyCycleException(newPath)
            }

            val dependencyNodes = resolveDependencies(dependencies, nodesAlreadyCreated, newPath)
            val command = containerCommandResolver.resolveCommand(container, task)
            val additionalEnvironmentVariables = additionalEnvironmentVariables(isRootNode)
            val additionalPortMappings = additionalPortMappings(isRootNode)

            ContainerDependencyGraphNode(container, command, additionalEnvironmentVariables, additionalPortMappings, isRootNode, dependencyNodes, this)
        }
    }

    private fun resolveDependencies(dependencies: Set<Container>, nodesAlreadyCreated: MutableMap<Container, ContainerDependencyGraphNode>, path: List<Container>): Set<ContainerDependencyGraphNode> {
        return dependencies
            .map { getOrCreateNode(it, nodesAlreadyCreated, path) }
            .toSet()
    }

    private fun findContainers(names: Set<String>, parentDescription: String): Set<Container> {
        return names.map { findContainer(it, parentDescription) }.toSet()
    }

    private fun findContainer(name: String, parentDescription: String): Container {
        val container = config.containers[name]

        if (container == null) {
            throw DependencyResolutionFailedException("The container '$name' referenced by $parentDescription does not exist.")
        }

        return container
    }

    private fun dependencyCycleException(path: List<Container>): DependencyResolutionFailedException {
        val introduction = "There is a dependency cycle in task '${task.name}'. "
        val isCycleDueToTaskDependency = task.dependsOnContainers.contains(path[1].name)
        val description = if (isCycleDueToTaskDependency) descriptionForTaskDependencyCycle(path) else descriptionForContainerDependencyCycle(path)

        return DependencyResolutionFailedException(introduction + description)
    }

    private fun descriptionForTaskDependencyCycle(path: List<Container>): String {
        val pathWithoutTaskContainer = path.drop(1)
        val names = pathWithoutTaskContainer.map { "'${it.name}'" }
        val descriptionOfFirst = "Container ${names.first()} (which is explicitly started by the task)"

        val isCycleBackToTaskContainer = path.last().name == task.runConfiguration.container
        val descriptionOfLast = if (isCycleBackToTaskContainer) "the task container ${names.last()}" else names.last()

        val containersWithOutgoingDependencies = listOf(descriptionOfFirst) + names.drop(1).dropLast(1)
        val containersWithIncomingDependencies = names.drop(1).dropLast(1) + descriptionOfLast

        return containersWithOutgoingDependencies
            .zip(containersWithIncomingDependencies) { outgoing, incoming -> "$outgoing depends on $incoming" }
            .joinToString(", and ") + "."
    }

    private fun descriptionForContainerDependencyCycle(path: List<Container>): String {
        val names = path.map { "'${it.name}'" }
        val firstContainer = names.first()
        val otherContainers = names.drop(1)
        return "Container $firstContainer depends on " + otherContainers.joinToString(", which depends on ") + "."
    }

    private fun additionalEnvironmentVariables(isRootNode: Boolean): Map<String, EnvironmentVariableExpression> =
        if (isRootNode) {
            task.runConfiguration.additionalEnvironmentVariables
        } else {
            emptyMap()
        }

    private fun additionalPortMappings(isRootNode: Boolean): Set<PortMapping> =
        if (isRootNode) {
            task.runConfiguration.additionalPortMappings
        } else {
            emptySet()
        }
}

data class ContainerDependencyGraphNode(
    val container: Container,
    val command: Command?,
    val additionalEnvironmentVariables: Map<String, EnvironmentVariableExpression>,
    val additionalPortMappings: Set<PortMapping>,
    val isRootNode: Boolean,
    val dependsOn: Set<ContainerDependencyGraphNode>,
    val graph: ContainerDependencyGraph
) {
    val dependedOnBy: Set<ContainerDependencyGraphNode> by lazy { graph.allNodes.filter { it.dependsOn.contains(this) }.toSet() }

    val dependsOnContainers: Set<Container> by lazy { dependsOn.map { it.container }.toSet() }
    val dependedOnByContainers: Set<Container> by lazy { dependedOnBy.map { it.container }.toSet() }
}

class DependencyResolutionFailedException(message: String) : Exception(message)
