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

package batect.config.io

import batect.config.Configuration
import batect.config.io.deserializers.PathDeserializer
import batect.logging.Logger
import batect.os.PathResolutionResult
import batect.os.PathResolverFactory
import com.charleskorn.kaml.EmptyYamlDocumentException
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlException
import kotlinx.serialization.context.SimpleModule
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

class ConfigurationLoader(
    private val pathResolverFactory: PathResolverFactory,
    private val logger: Logger
) {
    fun loadConfig(path: Path): Configuration {
        val absolutePath = path.toAbsolutePath()

        logger.info {
            message("Loading configuration.")
            data("path", absolutePath.toString())
        }

        if (!Files.exists(absolutePath)) {
            logger.error {
                message("Configuration file could not be found.")
                data("path", absolutePath.toString())
            }

            throw ConfigurationException("The file '$absolutePath' does not exist.")
        }

        val configFileContent = Files.readAllBytes(absolutePath).toString(Charset.defaultCharset())
        val config = loadConfig(configFileContent, absolutePath)

        logger.info {
            message("Configuration loaded.")
            data("config", config)
        }

        return config
    }

    private fun loadConfig(configFileContent: String, filePath: Path): Configuration {
        val pathResolver = pathResolverFactory.createResolver(filePath.parent)
        val pathDeserializer = PathDeserializer(pathResolver)
        val parser = Yaml()
        parser.install(SimpleModule(PathResolutionResult::class, pathDeserializer))

        try {
            return parser
                .parse(Configuration.serializer(), configFileContent)
                .withResolvedProjectName(pathResolver)
        } catch (e: Throwable) {
            logger.error {
                message("Exception thrown while loading configuration.")
                exception(e)
            }

            throw mapException(e, filePath.toString())
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
}
