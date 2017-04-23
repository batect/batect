package decompose.config.io

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext

fun <T> deserializeCollection(p: JsonParser?, context: DeserializationContext?, initialValue: T, accumulator: (String, T) -> T): T {
    if (p == null) {
        throw IllegalArgumentException("Must provide a JsonParser.")
    }

    if (context == null) {
        throw IllegalArgumentException("Must provide a DeserializationContext.")
    }

    if (!p.isExpectedStartArrayToken()) {
        context.reportWrongTokenException(p, JsonToken.START_ARRAY, "Expected a list.")
    }

    var result = initialValue

    while (true) {
        val token = p.nextToken()

        if (token == JsonToken.END_ARRAY) {
            return result
        }

        val rawValue = context.readValue(p, String::class.java)
        result = accumulator(rawValue, result)
    }
}
