/*
   Copyright 2017-2020 Charles Korn.

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

import com.charleskorn.kaml.IncorrectTypeException
import com.charleskorn.kaml.YamlInput
import kotlinx.serialization.SerialDescriptor

fun <T> YamlInput.tryToDeserializeWith(descriptor: SerialDescriptor, deserializationAction: (YamlInput) -> T): T? {
    try {
        val input = this.beginStructure(descriptor) as YamlInput
        val value = deserializationAction(input)
        input.endStructure(descriptor)
        return value
    } catch (_: IncorrectTypeException) {
        return null
    }
}
