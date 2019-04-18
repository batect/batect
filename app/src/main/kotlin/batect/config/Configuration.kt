/*
   Copyright 2017-2019 Charles Korn.

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

import batect.config.io.ConfigurationException
import batect.os.PathResolver
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Configuration(
    @SerialName("project_name") val projectName: String? = null,
    @Serializable(with = TaskMap.Companion::class) val tasks: TaskMap = TaskMap(),
    @Serializable(with = ContainerMap.Companion::class) val containers: ContainerMap = ContainerMap()
) {
    fun withResolvedProjectName(pathResolver: PathResolver): Configuration {
        if (projectName != null) {
            return this
        }

        if (pathResolver.relativeTo.root == pathResolver.relativeTo) {
            throw ConfigurationException("No project name has been given explicitly, but the configuration file is in the root directory and so a project name cannot be inferred.")
        }

        return this.copy(projectName = pathResolver.relativeTo.fileName.toString())
    }
}
