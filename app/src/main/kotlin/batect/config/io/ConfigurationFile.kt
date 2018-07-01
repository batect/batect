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

package batect.config.io

import batect.config.Configuration
import batect.config.Container
import batect.config.ContainerMap
import batect.config.Task
import batect.config.TaskMap

data class ConfigurationFile(
    val projectName: String?,
    val tasks: Map<String, TaskFromFile> = emptyMap(),
    val containers: Map<String, ContainerFromFile> = emptyMap()
) {

    fun toConfiguration(pathResolver: PathResolver): Configuration {

        return Configuration(
            resolveProjectName(pathResolver),
            TaskMap(tasks.map { (name, task) -> fromFileToTaskConfiguration(name, task) }),
            ContainerMap(containers.map { (name, container) -> fromFileToContainerConfiguration(name, container, pathResolver) }))
    }

    private fun fromFileToContainerConfiguration(containerName: String, fileContainerConfig: ContainerFromFile?, pathResolver: PathResolver): Container {
        if (fileContainerConfig == null) {
            throw ConfigurationException("Container '$containerName' is invalid: no properties have been provided. At least one of image or build_directory is required")
        }

        return fileContainerConfig.toContainer(containerName, pathResolver)
    }

    private fun fromFileToTaskConfiguration(taskName: String, fileTaskConfig: TaskFromFile?): Task {
        if (fileTaskConfig == null) {
            throw ConfigurationException("Task '$taskName' is invalid: no properties have been provided, container is required")
        }

        return fileTaskConfig.toTask(taskName)
    }

    private fun resolveProjectName(pathResolver: PathResolver): String {
        if (projectName != null) {
            return projectName
        }

        if (pathResolver.relativeTo.root == pathResolver.relativeTo) {
            throw ConfigurationException("No project name has been given explicitly, but the configuration file is in the root directory and so a project name cannot be inferred.")
        }

        return pathResolver.relativeTo.fileName.toString()
    }
}
