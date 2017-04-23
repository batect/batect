package decompose.config

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

class StringSetDeserializer() : StdDeserializer<Set<String>>(Set::class.java) {
    override fun deserialize(p: JsonParser?, context: DeserializationContext?): Set<String> {
        if (p == null) {
            throw IllegalArgumentException("Must provide a JsonParser.")
        }

        if (context == null) {
            throw IllegalArgumentException("Must provide a DeserializationContext.")
        }

        if (!p.isExpectedStartArrayToken()) {
            throw JsonMappingException.from(p, "Expected a list.")
        }

        val result = mutableSetOf<String>()

        while (true) {
            val token = p.nextToken()

            if (token == JsonToken.END_ARRAY) {
                return result.toSet()
            }

            val value = context.readValue(p, String::class.java)

            if (result.contains(value)) {
                throw JsonMappingException.from(p, "Duplicate value '$value'")
            }

            result.add(value)
        }
    }
}
