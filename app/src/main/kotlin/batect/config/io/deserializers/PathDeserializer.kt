/*
   Copyright 2017-2019 Charles Korn.

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

import batect.os.PathResolutionResult
import batect.os.PathResolver
import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.internal.StringDescriptor

class PathDeserializer(private val pathResolver: PathResolver) : KSerializer<PathResolutionResult> {
    override val descriptor: SerialDescriptor = StringDescriptor

    override fun deserialize(decoder: Decoder): PathResolutionResult = pathResolver.resolve(decoder.decodeString())

    override fun serialize(encoder: Encoder, obj: PathResolutionResult) = throw UnsupportedOperationException()
}
