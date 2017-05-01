package decompose.config.io

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

internal class StringSetDeserializer : StdDeserializer<Set<String>>(Set::class.java) {
    override fun deserialize(p: JsonParser?, context: DeserializationContext?): Set<String> {
        return deserializeCollection(p, context, setOf<String>()) { value, result ->
            if (result.contains(value)) {
                throw JsonMappingException.from(p, "Duplicate value '$value'")
            }

            result + value
        }
    }
}
