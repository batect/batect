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

package batect.execution

import batect.config.Configuration
import batect.config.Container
import batect.config.Expression
import batect.config.PortMapping
import batect.config.Task
import batect.primitives.mapToSet

data class ContainerDependencyGraph(
    private val config: Configuration,
    private val task: Task,
    private val commandResolver: ContainerCommandResolver,
    private val entrypointResolver: ContainerEntrypointResolver
) {
    private val runConfiguration = task.runConfiguration ?: throw IllegalArgumentException("Cannot create a container dependency graph for a task that only has prerequisites.")
    private val nodesMap = createNodes()

    val taskContainerNode: ContainerDependencyGraphNode by lazy { nodeFor(config.containers[runConfiguration.container]!!) }
    val allNodes = nodesMap.values
    val allContainers = allNodes.mapToSet { it.container }

    init {
        task.customisations.keys.forEach { customisationName ->
            if (allContainers.none { it.name == customisationName }) {
                throw CustomisationForContainerNotInTaskDependencyGraphException("The task '${task.name}' has customisations for container '$customisationName', but the container '$customisationName' will not be started as part of the task.")
            }
        }
    }

    fun nodeFor(container: Container): ContainerDependencyGraphNode {
        val node = nodesMap[container] ?: throw IllegalArgumentException("Container '${container.name}' is not part of this dependency graph.")

        return node
    }

    private fun createNodes(): Map<Container, ContainerDependencyGraphNode> {
        if (task.dependsOnContainers.contains(runConfiguration.container)) {
            throw MainTaskContainerIsDependencyException("The task '${task.name}' cannot have the container '${runConfiguration.container}' as both the main task container and also a dependency.")
        }

        val taskContainer = findContainer(runConfiguration.container, "task '${task.name}'")
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
                throw ContainerSelfDependencyException("The container '${container.name}' cannot depend on itself.")
            }

            val newPath = path + container

            if (path.contains(container)) {
                throw dependencyCycleException(newPath)
            }

            val dependencyNodes = resolveDependencies(dependencies, nodesAlreadyCreated, newPath)

            val overrides = ContainerRuntimeConfiguration(
                commandResolver.resolveCommand(container, task),
                entrypointResolver.resolveEntrypoint(container, task),
                workingDirectory(isRootNode, container),
                additionalEnvironmentVariables(isRootNode, container),
                additionalPortMappings(isRootNode, container)
            )

            ContainerDependencyGraphNode(
                container,
                overrides,
                isRootNode,
                dependencyNodes,
                this
            )
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
            throw ContainerDoesNotExistException("The container '$name' referenced by $parentDescription does not exist.")
        }

        return container
    }

    private fun dependencyCycleException(path: List<Container>): DependencyResolutionFailedException {
        val introduction = "There is a dependency cycle in task '${task.name}'. "
        val isCycleDueToTaskDependency = task.dependsOnContainers.contains(path[1].name)
        val description = if (isCycleDueToTaskDependency) descriptionForTaskDependencyCycle(path) else descriptionForContainerDependencyCycle(path)

        return ContainerDependencyCycleException(introduction + description)
    }

    private fun descriptionForTaskDependencyCycle(path: List<Container>): String {
        val pathWithoutTaskContainer = path.drop(1)
        val names = pathWithoutTaskContainer.map { "'${it.name}'" }
        val descriptionOfFirst = "Container ${names.first()} (which is explicitly started by the task)"

        val isCycleBackToTaskContainer = path.last().name == runConfiguration.container
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

    private fun additionalEnvironmentVariables(isRootNode: Boolean, container: Container): Map<String, Expression> =
        if (isRootNode) {
            runConfiguration.additionalEnvironmentVariables
        } else {
            task.customisations[container.name]?.additionalEnvironmentVariables ?: emptyMap()
        }

    private fun additionalPortMappings(isRootNode: Boolean, container: Container): Set<PortMapping> =
        if (isRootNode) {
            runConfiguration.additionalPortMappings
        } else {
            task.customisations[container.name]?.additionalPortMappings ?: emptySet()
        }

    private fun workingDirectory(isRootNode: Boolean, container: Container): String? = if (isRootNode) {
        runConfiguration.workingDiretory ?: container.workingDirectory
    } else {
        task.customisations[container.name]?.workingDirectory ?: container.workingDirectory
    }
}

sealed class DependencyResolutionFailedException(message: String) : Exception(message) {
    override fun toString(): String = message!!
}

class ContainerDependencyCycleException(message: String) : DependencyResolutionFailedException(message)
class ContainerDoesNotExistException(message: String) : DependencyResolutionFailedException(message)
class ContainerSelfDependencyException(message: String) : DependencyResolutionFailedException(message)
class MainTaskContainerIsDependencyException(message: String) : DependencyResolutionFailedException(message)
class CustomisationForContainerNotInTaskDependencyGraphException(message: String) : DependencyResolutionFailedException(message)
