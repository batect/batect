package decompose.config

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

internal class EnvironmentDeserializer() : StdDeserializer<Map<String, String>>(Map::class.java) {
    override fun deserialize(p: JsonParser?, context: DeserializationContext?): Map<String, String> {
        return deserializeCollection(p, context, mapOf<String, String>()) { rawValue, result ->
            val separator = '='
            val separatorIndex = rawValue.indexOf(separator)

            if (separatorIndex == -1) {
                throw JsonMappingException.from(p, "Environment variable definition is missing '$separator' separator.")
            }

            val key = rawValue.substring(0, separatorIndex)
            val value = rawValue.substring(separatorIndex + 1)

            if (result.containsKey(key)) {
                throw JsonMappingException.from(p, "Duplicate environment variable '$key'")
            }

            result + (key to value)
        }
    }
}
