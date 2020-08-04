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
import batect.config.GitInclude
import batect.config.Include
import batect.config.IncludeConfigSerializer
import batect.config.NamedObjectMap
import batect.config.TaskMap
import batect.config.includes.GitIncludePathResolutionContext
import batect.config.includes.IncludeResolver
import batect.config.io.deserializers.PathDeserializer
import batect.docker.DockerImageNameValidator
import batect.git.GitException
import batect.logging.LogMessageBuilder
import batect.logging.Logger
import batect.os.DefaultPathResolutionContext
import batect.os.PathResolutionResult
import batect.os.PathResolver
import batect.os.PathResolverFactory
import batect.primitives.flatMapToSet
import batect.primitives.mapToSet
import batect.utils.asHumanReadableList
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
    private val includeResolver: IncludeResolver,
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

        val rootConfigFile = loadConfigFile(absolutePathToRootConfigFile, null)
        val filesLoaded = mutableMapOf<Include, ConfigurationFile>(FileInclude(absolutePathToRootConfigFile) to rootConfigFile)
        val remainingIncludesToLoad = mutableSetOf<Include>()
        remainingIncludesToLoad += rootConfigFile.includes

        while (remainingIncludesToLoad.isNotEmpty()) {
            val includeToLoad = remainingIncludesToLoad.first()
            val pathToLoad = includeResolver.resolve(includeToLoad)
            val file = loadConfigFile(pathToLoad, includeToLoad)
            checkForProjectName(file, includeToLoad)

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

    private fun loadConfigFile(path: Path, includedAs: Include?): ConfigurationFile {
        logger.info {
            message("Loading configuration file.")
            data("path", path)

            if (includedAs != null) {
                data("includedAs", includedAs)
            }
        }

        val content = Files.readAllBytes(path).toString(Charset.defaultCharset())
        val pathResolver = pathResolverFor(path, includedAs)
        val pathDeserializer = PathDeserializer(pathResolver)
        val module = serializersModuleOf(PathResolutionResult::class, pathDeserializer)
        val config = YamlConfiguration(extensionDefinitionPrefix = ".", polymorphismStyle = PolymorphismStyle.Property)
        val parser = Yaml(configuration = config, context = module)

        try {
            val file = parser.parse(ConfigurationFile.serializer(), content)
            checkGitIncludesExist(file)

            logger.info {
                message("Configuration file loaded.")
                data("path", path)
                data("file", file)
            }

            return when (includedAs) {
                is GitInclude -> file.replaceFileIncludesWithGitIncludes(includedAs)
                else -> file
            }
        } catch (e: Throwable) {
            logger.error {
                message("Exception thrown while loading configuration.")
                data("path", path)
                exception(e)
            }

            throw mapException(e, path.toString(), includedAs)
        }
    }

    // File includes are checked as part of the deserialization process, so we don't need to check them here.
    private fun checkGitIncludesExist(file: ConfigurationFile) {
        file.includes
            .filterIsInstance<GitInclude>()
            .forEach { include ->
                val path = try {
                    includeResolver.resolve(include)
                } catch (e: GitException) {
                    throw ConfigurationException("Could not load include '${include.path}' from ${include.repo}@${include.ref}: ${e.message}", null, null, e)
                }

                if (!Files.exists(path)) {
                    throw ConfigurationException("Included file '${include.path}' (${include.path} from ${include.repo}@${include.ref}) does not exist.")
                }
            }
    }

    private fun checkForProjectName(file: ConfigurationFile, includedAs: Include) {
        if (file.projectName != null) {
            throw ConfigurationFileException("Only the root configuration file can contain the project name, but this file has a project name.", includedAs.toString())
        }
    }

    private fun inferProjectName(pathToRootConfigFile: Path): String {
        val projectDirectory = pathToRootConfigFile.parent

        if (projectDirectory.root == projectDirectory) {
            throw ConfigurationFileException("No project name has been given explicitly, but the configuration file is in the root directory and so a project name cannot be inferred.", pathToRootConfigFile.toString())
        }

        val inferredProjectName = projectDirectory.fileName.toString().toLowerCase()

        if (!DockerImageNameValidator.isValidImageName(inferredProjectName)) {
            throw ConfigurationFileException(
                "The inferred project name '$inferredProjectName' is invalid. The project name must be a valid Docker reference: it ${DockerImageNameValidator.validNameDescription}. Provide a valid project name explicitly with 'project_name'.",
                pathToRootConfigFile.toString()
            )
        }

        return inferredProjectName
    }

    private fun mergeContainers(filesLoaded: Map<Include, ConfigurationFile>): ContainerMap {
        checkForDuplicateDefinitions("container", filesLoaded) { it.containers }

        return ContainerMap(filesLoaded.flatMap { it.value.containers })
    }

    private fun mergeTasks(filesLoaded: Map<Include, ConfigurationFile>): TaskMap {
        checkForDuplicateDefinitions("task", filesLoaded) { it.tasks }

        return TaskMap(filesLoaded.flatMap { it.value.tasks })
    }

    private fun mergeConfigVariables(filesLoaded: Map<Include, ConfigurationFile>): ConfigVariableMap {
        checkForDuplicateDefinitions("config variable", filesLoaded) { it.configVariables }

        return ConfigVariableMap(filesLoaded.flatMap { it.value.configVariables })
    }

    private fun <T> checkForDuplicateDefinitions(type: String, filesLoaded: Map<Include, ConfigurationFile>, collectionSelector: (ConfigurationFile) -> NamedObjectMap<T>) {
        val allNames = filesLoaded.values.flatMapToSet { collectionSelector(it).keys }

        allNames.forEach { name ->
            val files = filesLoaded.filterValues { collectionSelector(it).containsKey(name) }.keys

            if (files.size > 1) {
                val formattedFileNames = files.map {
                    when (it) {
                        is FileInclude -> it.path.toString()
                        is GitInclude -> "${it.path} from ${it.repo}@${it.ref}"
                    }
                }.asHumanReadableList()

                throw ConfigurationException("The $type '$name' is defined in multiple files: $formattedFileNames")
            }
        }
    }

    private fun mapException(e: Throwable, filePath: String, includedAs: Include?): ConfigurationFileException {
        val pathToUse = includedAs?.toString() ?: filePath

        return when (e) {
            is YamlException -> ConfigurationFileException(mapYamlExceptionMessage(e), pathToUse, e.line, e.column, e)
            is ConfigurationException -> ConfigurationFileException(e.message, pathToUse, e.lineNumber, e.column, e)
            else -> ConfigurationFileException("Could not load configuration file: ${e.message}", pathToUse, null, null, e)
        }
    }

    private fun mapYamlExceptionMessage(e: YamlException): String = when (e) {
        is EmptyYamlDocumentException -> "File contains no configuration."
        else -> e.message
    }

    private fun pathResolverFor(file: Path, includedAs: Include?): PathResolver = when {
        includedAs is GitInclude -> pathResolverFactory.createResolver(GitIncludePathResolutionContext(file.parent, includeResolver.rootPathFor(includedAs.repositoryReference), includedAs))
        else -> pathResolverFactory.createResolver(DefaultPathResolutionContext(file.parent))
    }

    private fun ConfigurationFile.replaceFileIncludesWithGitIncludes(sourceInclude: GitInclude): ConfigurationFile =
        this.copy(includes = this.includes.mapToSet { include ->
            if (include is FileInclude) {
                val rootPath = includeResolver.rootPathFor(sourceInclude.repositoryReference)
                val newInclude = sourceInclude.copy(path = rootPath.relativize(include.path).toString())

                logger.info {
                    message("Rewrote file include in file included from Git.")
                    data("sourceInclude", sourceInclude)
                    data("oldInclude", include)
                    data("newInclude", newInclude)
                }

                newInclude
            } else {
                include
            }
        })
}

private fun LogMessageBuilder.data(key: String, value: Configuration) = this.data(key, value, Configuration.serializer())
private fun LogMessageBuilder.data(key: String, value: ConfigurationFile) = this.data(key, value, ConfigurationFile.serializer())
private fun LogMessageBuilder.data(key: String, value: Include) = this.data(key, value, IncludeConfigSerializer)
