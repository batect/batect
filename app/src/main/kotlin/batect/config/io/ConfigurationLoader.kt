/*
    Copyright 2017-2021 Charles Korn.

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
import batect.config.ConfigurationFile
import batect.config.ContainerMap
import batect.config.FileInclude
import batect.config.GitInclude
import batect.config.Include
import batect.config.IncludeConfigSerializer
import batect.config.NamedObjectMap
import batect.config.RawConfiguration
import batect.config.TaskMap
import batect.config.includes.GitIncludePathResolutionContext
import batect.config.includes.GitRepositoryCacheNotificationListener
import batect.config.includes.IncludeResolver
import batect.config.io.deserializers.PathDeserializer
import batect.docker.ImageNameValidator
import batect.git.GitException
import batect.logging.LogMessageBuilder
import batect.logging.Logger
import batect.os.DefaultPathResolutionContext
import batect.os.PathResolutionResult
import batect.os.PathResolver
import batect.os.PathResolverFactory
import batect.primitives.flatMapToSet
import batect.primitives.mapToSet
import batect.telemetry.TelemetrySessionBuilder
import batect.utils.asHumanReadableList
import com.charleskorn.kaml.EmptyYamlDocumentException
import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlException
import kotlinx.serialization.modules.serializersModuleOf
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

class ConfigurationLoader(
    private val includeResolver: IncludeResolver,
    private val pathResolverFactory: PathResolverFactory,
    private val telemetrySessionBuilder: TelemetrySessionBuilder,
    private val defaultGitRepositoryCacheNotificationListener: GitRepositoryCacheNotificationListener,
    private val logger: Logger
) {
    fun loadConfig(rootConfigFilePath: Path, gitRepositoryCacheNotificationListener: GitRepositoryCacheNotificationListener = defaultGitRepositoryCacheNotificationListener): ConfigurationLoadResult {
        return telemetrySessionBuilder.addSpan("LoadConfiguration") { span ->
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

            val rootConfigFile = loadConfigFile(absolutePathToRootConfigFile, null, gitRepositoryCacheNotificationListener)
            val pathsLoaded = mutableSetOf(absolutePathToRootConfigFile)
            val filesLoaded = mutableMapOf<Include, ConfigurationFile>(FileInclude(absolutePathToRootConfigFile) to rootConfigFile)
            val remainingIncludesToLoad = mutableSetOf<Include>()
            remainingIncludesToLoad += rootConfigFile.includes

            while (remainingIncludesToLoad.isNotEmpty()) {
                val includeToLoad = remainingIncludesToLoad.first()
                val pathToLoad = includeResolver.resolve(includeToLoad, gitRepositoryCacheNotificationListener)
                pathsLoaded.add(pathToLoad)

                val file = loadConfigFile(pathToLoad, includeToLoad, gitRepositoryCacheNotificationListener)
                checkForProjectName(file, includeToLoad)

                filesLoaded[includeToLoad] = file
                remainingIncludesToLoad.remove(includeToLoad)
                remainingIncludesToLoad += file.includes
                remainingIncludesToLoad -= filesLoaded.keys
            }

            val projectName = rootConfigFile.projectName ?: inferProjectName(absolutePathToRootConfigFile)
            val config = RawConfiguration(projectName, mergeTasks(filesLoaded), mergeContainers(filesLoaded), mergeConfigVariables(filesLoaded))

            logger.info {
                message("Configuration loaded.")
                data("config", config)
            }

            span.addAttribute("containerCount", config.containers.size)
            span.addAttribute("taskCount", config.tasks.size)
            span.addAttribute("configVariableCount", config.configVariables.size)
            span.addAttribute("fileIncludeCount", filesLoaded.keys.count { it is FileInclude } - 1)
            span.addAttribute("gitIncludeCount", filesLoaded.keys.count { it is GitInclude })

            ConfigurationLoadResult(config, pathsLoaded)
        }
    }

    private fun loadConfigFile(path: Path, includedAs: Include?, gitRepositoryCacheNotificationListener: GitRepositoryCacheNotificationListener): ConfigurationFile {
        logger.info {
            message("Loading configuration file.")
            data("path", path)

            if (includedAs != null) {
                data("includedAs", includedAs)
            }
        }

        val content = Files.readAllBytes(path).toString(Charset.defaultCharset())
        val pathResolver = pathResolverFor(path, includedAs, gitRepositoryCacheNotificationListener)
        val pathDeserializer = PathDeserializer(pathResolver)
        val module = serializersModuleOf(PathResolutionResult::class, pathDeserializer)
        val config = YamlConfiguration(extensionDefinitionPrefix = ".", polymorphismStyle = PolymorphismStyle.Property)
        val parser = Yaml(configuration = config, serializersModule = module)

        try {
            val file = parser.decodeFromString(ConfigurationFile.serializer(), content)
            checkGitIncludesExist(file, gitRepositoryCacheNotificationListener)

            logger.info {
                message("Configuration file loaded.")
                data("path", path)
                data("file", file)
            }

            return when (includedAs) {
                is GitInclude -> file.replaceFileIncludesWithGitIncludes(includedAs, gitRepositoryCacheNotificationListener)
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
    private fun checkGitIncludesExist(file: ConfigurationFile, gitRepositoryCacheNotificationListener: GitRepositoryCacheNotificationListener) {
        file.includes
            .filterIsInstance<GitInclude>()
            .forEach { include ->
                val path = try {
                    includeResolver.resolve(include, gitRepositoryCacheNotificationListener)
                } catch (e: GitException) {
                    throw ConfigurationException("Could not load include '${include.path}' from ${include.repo}@${include.ref}: ${e.message}", null, null, null, e)
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

        val inferredProjectName = projectDirectory.fileName.toString().lowercase()

        if (!ImageNameValidator.isValidImageName(inferredProjectName)) {
            throw ConfigurationFileException(
                "The inferred project name '$inferredProjectName' is invalid. The project name must be a valid Docker reference: it ${ImageNameValidator.validNameDescription}. Provide a valid project name explicitly with 'project_name'.",
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
            is YamlException -> ConfigurationFileException(mapYamlExceptionMessage(e), pathToUse, e.path, e)
            is ConfigurationException -> ConfigurationFileException(e.message, pathToUse, e.lineNumber, e.column, e.path)
            else -> ConfigurationFileException("Could not load configuration file: ${e.message}", pathToUse, null, null, null, e)
        }
    }

    private fun mapYamlExceptionMessage(e: YamlException): String = when (e) {
        is EmptyYamlDocumentException -> "File contains no configuration."
        else -> e.message
    }

    private fun pathResolverFor(file: Path, includedAs: Include?, gitRepositoryCacheNotificationListener: GitRepositoryCacheNotificationListener): PathResolver = when (includedAs) {
        is GitInclude -> pathResolverFactory.createResolver(GitIncludePathResolutionContext(file.parent, includeResolver.rootPathFor(includedAs.repositoryReference, gitRepositoryCacheNotificationListener), includedAs))
        else -> pathResolverFactory.createResolver(DefaultPathResolutionContext(file.parent))
    }

    private fun ConfigurationFile.replaceFileIncludesWithGitIncludes(sourceInclude: GitInclude, gitRepositoryCacheNotificationListener: GitRepositoryCacheNotificationListener): ConfigurationFile =
        this.copy(
            includes = this.includes.mapToSet { include ->
                if (include is FileInclude) {
                    val rootPath = includeResolver.rootPathFor(sourceInclude.repositoryReference, gitRepositoryCacheNotificationListener)
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
            }
        )
}

private fun LogMessageBuilder.data(key: String, value: RawConfiguration) = this.data(key, value, RawConfiguration.serializer())
private fun LogMessageBuilder.data(key: String, value: ConfigurationFile) = this.data(key, value, ConfigurationFile.serializer())
private fun LogMessageBuilder.data(key: String, value: Include) = this.data(key, value, IncludeConfigSerializer)

data class ConfigurationLoadResult(
    val configuration: RawConfiguration,
    val pathsLoaded: Set<Path>
)
