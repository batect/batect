package decompose.config.io

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import decompose.config.Configuration
import java.io.InputStream

class ConfigurationLoader(val pathResolver: PathResolver) {
    fun loadConfig(configurationStream: InputStream, fileName: String): Configuration {
        val mapper = ObjectMapper(YAMLFactory())
        mapper.propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE
        mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        mapper.registerModule(KotlinModule())

        try {
            return mapper.readValue(configurationStream, ConfigurationFile::class.java)
                    .toConfiguration(pathResolver)
        } catch (e: Throwable) {
            throw mapException(e, fileName)
        }
    }

    private fun mapException(e: Throwable, fileName: String): ConfigurationException = when (e) {
        is JsonProcessingException -> ConfigurationException(messageForJsonProcessingException(e, fileName), fileName, e.location.lineNr, e.location.columnNr, e)
        else -> ConfigurationException("Could not load '$fileName': ${e.message}", fileName, null, null, e)
    }

    private fun messageForJsonProcessingException(e: JsonProcessingException, fileName: String): String = when {
        e is UnrecognizedPropertyException -> "Unknown field '${e.propertyName}'"
        e is MissingKotlinParameterException -> "Missing required field '${e.path.last().fieldName}'"
        e.originalMessage == "No content to map due to end-of-input" -> "File '$fileName' is empty"
        e.cause is JsonParseException -> (e.cause as JsonParseException).originalMessage
        else -> e.originalMessage
    }
}
