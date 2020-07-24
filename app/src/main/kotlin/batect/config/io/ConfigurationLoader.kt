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

package batect.config.io

import batect.config.ConfigVariableMap
import batect.config.Configuration
import batect.config.ConfigurationFile
import batect.config.ContainerMap
import batect.config.FileInclude
import batect.config.NamedObjectMap
import batect.config.TaskMap
import batect.config.io.deserializers.PathDeserializer
import batect.docker.DockerImageNameValidator
import batect.logging.LogMessageBuilder
import batect.logging.Logger
import batect.os.PathResolutionResult
import batect.os.PathResolver
import batect.os.PathResolverFactory
import batect.primitives.flatMapToSet
import com.charleskorn.kaml.EmptyYamlDocumentException
import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.modules.serializersModuleOf

class ConfigurationLoader(
    private val pathResolverFactory: PathResolverFactory,
    private val logger: Logger
) {
    fun loadConfig(rootConfigFilePath: Path): Configuration {
        val absolutePathToRootConfigFile = rootConfigFilePath.toAbsolutePath()

        logger.info {
            message("Loading configuration.")
            data("rootConfigFilePath", absolutePathToRootConfigFile)
        }

        if (!Files.exists(absolutePathToRootConfigFile)) {
            logger.error {
                message("Root configuration file could not be found.")
                data("rootConfigFilePath", absolutePathToRootConfigFile)
            }

            throw ConfigurationException("The file '$absolutePathToRootConfigFile' does not exist.")
        }

        val rootConfigFile = loadConfigFile(absolutePathToRootConfigFile)
        val filesLoaded = mutableMapOf(FileInclude(absolutePathToRootConfigFile) to rootConfigFile)
        val remainingIncludesToLoad = mutableSetOf<FileInclude>()
        remainingIncludesToLoad += rootConfigFile.includes

        while (remainingIncludesToLoad.isNotEmpty()) {
            val includeToLoad = remainingIncludesToLoad.first()
            val pathToLoad = includeToLoad.path
            val file = loadConfigFile(pathToLoad)
            checkForProjectName(file, pathToLoad)

            filesLoaded[includeToLoad] = file
            remainingIncludesToLoad.remove(includeToLoad)
            remainingIncludesToLoad += file.includes
            remainingIncludesToLoad -= filesLoaded.keys
        }

        val projectName = rootConfigFile.projectName ?: inferProjectName(absolutePathToRootConfigFile)
        val config = Configuration(projectName, mergeTasks(filesLoaded), mergeContainers(filesLoaded), mergeConfigVariables(filesLoaded))

        logger.info {
            message("Configuration loaded.")
            data("config", config)
        }

        return config
    }

    private fun loadConfigFile(path: Path): ConfigurationFile {
        logger.info {
            message("Loading configuration file.")
            data("path", path)
        }

        val content = Files.readAllBytes(path).toString(Charset.defaultCharset())
        val pathResolver = pathResolverFor(path)
        val pathDeserializer = PathDeserializer(pathResolver)
        val module = serializersModuleOf(PathResolutionResult::class, pathDeserializer)
        val config = YamlConfiguration(extensionDefinitionPrefix = ".", polymorphismStyle = PolymorphismStyle.Property)
        val parser = Yaml(configuration = config, context = module)

        try {
            val file = parser.parse(ConfigurationFile.serializer(), content)

            logger.info {
                message("Configuration file loaded.")
                data("path", path)
                data("file", file)
            }

            return file
        } catch (e: Throwable) {
            logger.error {
                message("Exception thrown while loading configuration.")
                data("path", path)
                exception(e)
            }

            throw mapException(e, path.toString())
        }
    }

    private fun checkForProjectName(file: ConfigurationFile, path: Path) {
        if (file.projectName != null) {
            throw ConfigurationException("Only the root configuration file can contain the project name, but this file has a project name.", path.toString())
        }
    }

    private fun inferProjectName(pathToRootConfigFile: Path): String {
        val pathResolver = pathResolverFor(pathToRootConfigFile)

        if (pathResolver.relativeTo.root == pathResolver.relativeTo) {
            throw ConfigurationException("No project name has been given explicitly, but the configuration file is in the root directory and so a project name cannot be inferred.", pathToRootConfigFile.toString())
        }

        val inferredProjectName = pathResolver.relativeTo.fileName.toString().toLowerCase()

        if (!DockerImageNameValidator.isValidImageName(inferredProjectName)) {
            throw ConfigurationException(
                "The inferred project name '$inferredProjectName' is invalid. The project name must be a valid Docker reference: it ${DockerImageNameValidator.validNameDescription}. Provide a valid project name explicitly with 'project_name'.",
                pathToRootConfigFile.toString()
            )
        }

        return inferredProjectName
    }

    private fun mergeContainers(filesLoaded: Map<FileInclude, ConfigurationFile>): ContainerMap {
        checkForDuplicateDefinitions("container", filesLoaded) { it.containers }

        return ContainerMap(filesLoaded.flatMap { it.value.containers })
    }

    private fun mergeTasks(filesLoaded: Map<FileInclude, ConfigurationFile>): TaskMap {
        checkForDuplicateDefinitions("task", filesLoaded) { it.tasks }

        return TaskMap(filesLoaded.flatMap { it.value.tasks })
    }

    private fun mergeConfigVariables(filesLoaded: Map<FileInclude, ConfigurationFile>): ConfigVariableMap {
        checkForDuplicateDefinitions("config variable", filesLoaded) { it.configVariables }

        return ConfigVariableMap(filesLoaded.flatMap { it.value.configVariables })
    }

    private fun <T> checkForDuplicateDefinitions(type: String, filesLoaded: Map<FileInclude, ConfigurationFile>, collectionSelector: (ConfigurationFile) -> NamedObjectMap<T>) {
        val allNames = filesLoaded.values.flatMapToSet { collectionSelector(it).keys }

        allNames.forEach { name ->
            val files = filesLoaded.filterValues { collectionSelector(it).containsKey(name) }.keys

            if (files.size > 1) {
                throw ConfigurationException("The $type '$name' is defined in multiple files: ${files.joinToString(", ")}")
            }
        }
    }

    private fun mapException(e: Throwable, fileName: String): ConfigurationException = when (e) {
        is YamlException -> ConfigurationException(mapYamlExceptionMessage(e, fileName), fileName, e.line, e.column, e)
        is ConfigurationException -> ConfigurationException(e.message, e.fileName ?: fileName, e.lineNumber, e.column, e)
        else -> ConfigurationException("Could not load configuration file: ${e.message}", fileName, null, null, e)
    }

    private fun mapYamlExceptionMessage(e: YamlException, fileName: String): String = when (e) {
        is EmptyYamlDocumentException -> "File '$fileName' contains no configuration."
        else -> e.message
    }

    private fun pathResolverFor(file: Path): PathResolver = pathResolverFactory.createResolver(file.parent)
}

private fun LogMessageBuilder.data(key: String, value: Configuration) = this.data(key, value, Configuration.serializer())
private fun LogMessageBuilder.data(key: String, value: ConfigurationFile) = this.data(key, value, ConfigurationFile.serializer())
