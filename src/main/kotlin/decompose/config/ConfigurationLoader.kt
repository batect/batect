package decompose.config

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import java.io.InputStream

class ConfigurationLoader {
    fun loadConfig(configurationStream: InputStream, fileName: String): ConfigurationFile {
        val mapper = ObjectMapper(YAMLFactory())
        mapper.propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE
        mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        mapper.registerModule(KotlinModule())

        try {
            return mapper.readValue(configurationStream, ConfigurationFile::class.java)
        } catch (e: Throwable) {
            throw mapException(e, fileName)
        }
    }

    private fun mapException(e: Throwable, fileName: String): ConfigurationException = when {
        e is JsonProcessingException -> ConfigurationException(messageForJsonProcessingException(e, fileName), fileName, e.location.lineNr, e.location.columnNr, e)
        else -> ConfigurationException("Could not load '$fileName': ${e.message}", fileName, null, null, e)
    }

    private fun messageForJsonProcessingException(e: JsonProcessingException, fileName: String): String = when {
        e is UnrecognizedPropertyException -> "Unknown field '${e.propertyName}'"
        e is MissingKotlinParameterException -> "Missing required field '${e.path.last().fieldName}'"
        e.originalMessage == "No content to map due to end-of-input" -> "File '$fileName' is empty"
        else -> e.originalMessage
    }
}
