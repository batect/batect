/*
   Copyright 2017 Charles Korn.

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

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import batect.config.Configuration
import java.io.InputStream
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class ConfigurationLoader(val pathResolverFactory: PathResolverFactory, val fileSystem: FileSystem) {
    fun loadConfig(fileName: String): Configuration {
        val path = fileSystem.getPath(fileName).toAbsolutePath()

        if (!Files.exists(path)) {
            throw ConfigurationException("The file '$path' does not exist.")
        }

        Files.newInputStream(path, StandardOpenOption.READ).use {
            return loadConfig(it, path)
        }
    }

    private fun loadConfig(configurationStream: InputStream, filePath: Path): Configuration {
        val mapper = ObjectMapper(YAMLFactory())
        mapper.propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE
        mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        mapper.registerModule(KotlinModule())

        val pathResolver = pathResolverFactory.createResolver(filePath.parent)

        try {
            return mapper.readValue(configurationStream, ConfigurationFile::class.java)
                    .toConfiguration(pathResolver)
        } catch (e: Throwable) {
            throw mapException(e, filePath.toString())
        }
    }

    private fun mapException(e: Throwable, fileName: String): ConfigurationException = when (e) {
        is JsonProcessingException -> ConfigurationException(messageForJsonProcessingException(e, fileName), fileName, e.location.lineNr, e.location.columnNr, e)
        else -> ConfigurationException("Could not load configuration file: ${e.message}", fileName, null, null, e)
    }

    private fun messageForJsonProcessingException(e: JsonProcessingException, fileName: String): String = when {
        e is UnrecognizedPropertyException -> "Unknown field '${e.propertyName}'"
        e is MissingKotlinParameterException -> "Missing required field '${e.path.last().fieldName}'"
        e.originalMessage == "No content to map due to end-of-input" -> "File '$fileName' is empty"
        e.cause is JsonParseException -> (e.cause as JsonParseException).originalMessage
        else -> e.originalMessage
    }
}
