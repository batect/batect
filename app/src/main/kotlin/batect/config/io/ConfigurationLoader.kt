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
import batect.logging.Logger
import batect.os.PathResolverFactory
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.fasterxml.jackson.databind.util.ClassUtil
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

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

        Files.newInputStream(absolutePath, StandardOpenOption.READ).use { stream ->
            val config = loadConfig(stream, absolutePath)

            logger.info {
                message("Configuration loaded.")
                data("config", config)
            }

            return config
        }
    }

    private fun loadConfig(configurationStream: InputStream, filePath: Path): Configuration {
        val mapper = ObjectMapper(YAMLFactory())
        mapper.propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE
        mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        mapper.registerModule(KotlinModule())
        mapper.registerModule(JacksonModule())

        val pathResolver = pathResolverFactory.createResolver(filePath.parent)

        try {
            return mapper.readValue(configurationStream, ConfigurationFile::class.java)
                .toConfiguration(pathResolver)
        } catch (e: Throwable) {
            logger.error {
                message("Exception thrown while loading configuration.")
                exception(e)
            }

            throw mapException(e, filePath.toString())
        }
    }

    private fun mapException(e: Throwable, fileName: String): ConfigurationException = when (e) {
        is JsonProcessingException -> ConfigurationException(messageForJsonProcessingException(e, fileName), fileName, e.location.lineNr, e.location.columnNr, e)
        else -> ConfigurationException("Could not load configuration file: ${e.message}", fileName, null, null, e)
    }

    private fun messageForJsonProcessingException(e: JsonProcessingException, fileName: String): String = when {
        e is UnrecognizedPropertyException -> messageForUnrecognizedPropertyException(e)
        e is MissingKotlinParameterException -> "Missing required field '${e.path.last().fieldName}'"
        e is InvalidFormatException -> messageForInvalidFormatException(e)
        e is InvalidDefinitionException -> messageForInvalidDefinitionException(e)
        e.originalMessage == "No content to map due to end-of-input" -> "File '$fileName' is empty"
        e.cause is JsonParseException -> (e.cause as JsonParseException).originalMessage
        else -> e.originalMessage
    }

    private fun messageForUnrecognizedPropertyException(e: UnrecognizedPropertyException): String {
        val validProperties = e.knownPropertyIds
            .map { it as String }
            .sorted()
            .joinToString(", ")

        return "Unknown field '${e.propertyName}' (fields permitted here: $validProperties)"
    }

    private fun messageForInvalidFormatException(e: InvalidFormatException): String {
        // There are some edge cases where the message won't be in the format we expect, so in those cases, we should just show the whole original message.
        val message = e.originalMessage.substringAfter("Cannot deserialize value of type ${ClassUtil.nameOf(e.targetType)} from String \"${e.value}\": ")

        return "Value '${e.value}' for field '${e.path.last().fieldName}' is invalid: $message"
    }

    private fun messageForInvalidDefinitionException(e: InvalidDefinitionException): String {
        // There are some edge cases where the message won't be in the format we expect, so in those cases, we should just show the whole original message.
        return e.originalMessage.substringAfter("Cannot construct instance of ${ClassUtil.nameOf(e.type.rawClass)}, problem: ")
    }
}
