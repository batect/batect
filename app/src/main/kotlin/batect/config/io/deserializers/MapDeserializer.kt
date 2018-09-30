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

package batect.config.io.deserializers

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.type.TypeFactory

abstract class MapDeserializer<T>(private val klass: Class<T>) : StdDeserializer<Map<String, T>>(Map::class.java) {
    override fun deserialize(p: JsonParser?, context: DeserializationContext?): Map<String, T> {
        if (p == null) {
            throw IllegalArgumentException("Must provide a JsonParser.")
        }

        if (context == null) {
            throw IllegalArgumentException("Must provide a DeserializationContext.")
        }

        if (!p.isExpectedStartObjectToken) {
            context.reportWrongTokenException(TypeFactory.defaultInstance().constructMapType(Map::class.java, String::class.java, klass), JsonToken.START_OBJECT, "Expected an object")
        }

        var resultSoFar = emptyMap<String, T>()

        while (true) {
            val name = getEntryName(p, context)

            if (name == null) {
                return resultSoFar
            }

            val value = getValue(p, context, name)

            resultSoFar = resultSoFar + (name to value)
        }
    }

    private fun getEntryName(p: JsonParser, context: DeserializationContext): String? {
        try {
            val token = p.nextToken()

            if (token == JsonToken.END_OBJECT) {
                return null
            }

            if (token != JsonToken.FIELD_NAME) {
                context.reportWrongTokenException(String::class.java, JsonToken.FIELD_NAME, "Expected a field name")
            }

            return p.currentName
        } catch (e: JsonParseException) {
            if (e.originalMessage.startsWith("Duplicate field '")) {
                val name = e.originalMessage.substringAfter("Duplicate field ")

                throw JsonMappingException.from(p, getDuplicateEntryErrorMessage(name))
            } else {
                throw e
            }
        }
    }

    private fun getValue(p: JsonParser, context: DeserializationContext, name: String): T {
        val token = p.nextToken()

        if (token == JsonToken.VALUE_NULL) {
            throw JsonMappingException.from(p, getNullEntryErrorMessage(name))
        }

        return context.readValue(p, klass)
    }

    protected abstract fun getDuplicateEntryErrorMessage(name: String): String
    protected abstract fun getNullEntryErrorMessage(name: String): String
}
