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
