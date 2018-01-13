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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

internal class EnvironmentDeserializer : StdDeserializer<Map<String, String>>(Map::class.java) {
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
