/*
    Copyright 2017-2022 Charles Korn.

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

package batect.docker

import batect.dockerclient.ContainerReference
import batect.dockerclient.ImageReference
import batect.dockerclient.NetworkReference
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ContainerReferenceSerializer : KSerializer<ContainerReference> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ContainerReference") {
        element("id", String.serializer().descriptor)
    }

    override fun deserialize(decoder: Decoder): ContainerReference = throw UnsupportedOperationException()

    override fun serialize(encoder: Encoder, value: ContainerReference) {
        val output = encoder.beginStructure(descriptor)
        output.encodeStringElement(descriptor, 0, value.id)
        output.endStructure(descriptor)
    }
}

object ImageReferenceSerializer : KSerializer<ImageReference> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ImageReference") {
        element("id", String.serializer().descriptor)
    }

    override fun deserialize(decoder: Decoder): ImageReference = throw UnsupportedOperationException()

    override fun serialize(encoder: Encoder, value: ImageReference) {
        val output = encoder.beginStructure(descriptor)
        output.encodeStringElement(descriptor, 0, value.id)
        output.endStructure(descriptor)
    }
}

object NetworkReferenceSerializer : KSerializer<NetworkReference> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("NetworkReference") {
        element("id", String.serializer().descriptor)
    }

    override fun deserialize(decoder: Decoder): NetworkReference = throw UnsupportedOperationException()

    override fun serialize(encoder: Encoder, value: NetworkReference) {
        val output = encoder.beginStructure(descriptor)
        output.encodeStringElement(descriptor, 0, value.id)
        output.endStructure(descriptor)
    }
}
