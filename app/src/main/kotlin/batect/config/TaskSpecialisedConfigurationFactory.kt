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

package batect.config

import batect.cli.CommandLineOptions
import batect.config.io.ConfigurationException
import batect.execution.ContainerDoesNotExistException
import batect.logging.Logger
import batect.os.Command

class TaskSpecialisedConfigurationFactory(
    private val rawConfiguration: RawConfiguration,
    private val commandLineOptions: CommandLineOptions,
    private val logger: Logger
) {
    fun create(task: Task): TaskSpecialisedConfiguration {
        val overrides = commandLineOptions.imageOverrides.mapValues { PullImage(it.value) }
        val updatedContainers = rawConfiguration.containers
            .applyImageOverrides(overrides)
            .applyMainTaskContainerOverrides(task)
            .applyDependencyCustomisations(task)

        val taskSpecialisedConfiguration = TaskSpecialisedConfiguration(rawConfiguration.projectName, rawConfiguration.tasks, updatedContainers, rawConfiguration.configVariables)

        logger.info {
            message("Created task-specialised configuration.")
            data("config", taskSpecialisedConfiguration, TaskSpecialisedConfiguration.serializer())
        }

        return taskSpecialisedConfiguration
    }

    private fun ContainerMap.applyImageOverrides(overrides: Map<String, ImageSource>): ContainerMap =
        applyChanges(overrides, "override image for container") { container, override ->
            container.copy(imageSource = override)
        }

    private fun ContainerMap.applyMainTaskContainerOverrides(task: Task): ContainerMap {
        if (task.runConfiguration == null) {
            return this
        }

        val originalContainer = this[task.runConfiguration.container] ?: throw ContainerDoesNotExistException("The container '${task.runConfiguration.container}' referenced by task '${task.name}' does not exist.")

        val updatedContainer = originalContainer.copy(
            command = resolveCommandForMainContainer(originalContainer, task),
            entrypoint = task.runConfiguration.entrypoint ?: originalContainer.entrypoint,
            workingDirectory = task.runConfiguration.workingDiretory ?: originalContainer.workingDirectory,
            environment = originalContainer.environment + task.runConfiguration.additionalEnvironmentVariables,
            portMappings = originalContainer.portMappings + task.runConfiguration.additionalPortMappings
        )

        return ContainerMap(values - originalContainer + updatedContainer)
    }

    private fun resolveCommandForMainContainer(container: Container, task: Task): Command? {
        val baseCommand = task.runConfiguration!!.command ?: container.command

        if (commandLineOptions.additionalTaskCommandArguments.none() || !extraArgsApply(container, task)) {
            return baseCommand
        }

        if (baseCommand == null) {
            throw ContainerCommandResolutionException("Additional command line arguments for the task have been provided, but neither the task (${task.name}) nor the main task container (${container.name}) have an explicit command in the configuration file.")
        }

        return baseCommand + commandLineOptions.additionalTaskCommandArguments
    }

    private fun isTaskContainer(container: Container, task: Task): Boolean = container.name == task.runConfiguration?.container
    private fun isMainTask(task: Task): Boolean = task.name == commandLineOptions.taskName
    private fun extraArgsApply(container: Container, task: Task): Boolean = isTaskContainer(container, task) && isMainTask(task)

    // We don't need to check for customisations to the main task container here - this is checked at deserialization time by the serializer for Container.
    private fun ContainerMap.applyDependencyCustomisations(task: Task): ContainerMap = applyChanges(task.customisations, "apply customisations to container") { container, customisation ->
        container.copy(
            workingDirectory = customisation.workingDirectory ?: container.workingDirectory,
            environment = container.environment + customisation.additionalEnvironmentVariables,
            portMappings = container.portMappings + customisation.additionalPortMappings
        )
    }

    private fun <C> ContainerMap.applyChanges(source: Map<String, C>, changeDescription: String, generator: (Container, C) -> Container): ContainerMap {
        val updatedValues = source.entries.fold(values) { updatedContainers, change ->
            val containerName = change.key
            val oldContainer = this[containerName] ?: throw ConfigurationException("Cannot $changeDescription '$containerName' because there is no container named '$containerName' defined.")
            val newContainer = generator(oldContainer, change.value)

            updatedContainers - oldContainer + newContainer
        }

        return ContainerMap(updatedValues)
    }
}

class ContainerCommandResolutionException(message: String) : Exception(message)
