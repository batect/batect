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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.type.TypeFactory

inline fun <reified T> deserializeCollection(p: JsonParser?, context: DeserializationContext?, initialValue: T, accumulator: (String, T) -> T): T {
    if (p == null) {
        throw IllegalArgumentException("Must provide a JsonParser.")
    }

    if (context == null) {
        throw IllegalArgumentException("Must provide a DeserializationContext.")
    }

    if (!p.isExpectedStartArrayToken) {
        context.reportWrongTokenException(TypeFactory.defaultInstance().constructRawCollectionLikeType(T::class.java), JsonToken.START_ARRAY, "Expected a list.")
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
