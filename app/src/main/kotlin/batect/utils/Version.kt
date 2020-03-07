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

package batect.utils

import kotlinx.serialization.Decoder
import kotlinx.serialization.Encoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PrimitiveDescriptor
import kotlinx.serialization.PrimitiveKind
import kotlinx.serialization.SerialDescriptor
import kotlinx.serialization.Serializer

data class Version(val major: Int, val minor: Int, val patch: Int, val suffix: String = "", val metadata: String = "") : Comparable<Version> {
    override fun compareTo(other: Version): Int = this.compareTo(other, VersionComparisonMode.Normal)

    fun compareTo(other: Version, mode: VersionComparisonMode): Int = when {
        major != other.major -> major.compareTo(other.major)
        minor != other.minor -> minor.compareTo(other.minor)
        patch != other.patch -> patch.compareTo(other.patch)
        mode == VersionComparisonMode.DockerStyle -> suffix.compareTo(other.suffix)
        suffix == other.suffix -> 0
        suffix == "" -> 1
        other.suffix == "" -> -1
        else -> suffix.compareTo(other.suffix)
    }

    override fun toString(): String = when {
        suffix != "" && metadata != "" -> "$major.$minor.$patch-$suffix+$metadata"
        suffix != "" -> "$major.$minor.$patch-$suffix"
        metadata != "" -> "$major.$minor.$patch+$metadata"
        patch != 0 -> "$major.$minor.$patch"
        else -> "$major.$minor"
    }

    @Serializer(forClass = Version::class)
    companion object : KSerializer<Version> {
        private val regex = """^(?<major>\d+)(\.(?<minor>\d+)(\.(?<patch>\d+)(-(?<suffix>[a-zA-Z0-9-.]+))?(\+(?<metadata>[a-zA-Z0-9-.]+))?)?)?$""".toRegex()

        fun parse(value: String): Version {
            val match = regex.matchEntire(value)

            if (match == null) {
                throw IllegalArgumentException("The value '$value' is not recognised as a valid version.")
            }

            val major = match.getIntegerMatch(1)
            val minor = match.getIntegerMatch(3)
            val patch = match.getIntegerMatch(5)
            val suffix = match.getStringMatch(7)
            val metadata = match.getStringMatch(9)

            return Version(major, minor, patch, suffix, metadata)
        }

        private fun MatchResult.getIntegerMatch(index: Int): Int {
            val group = this.groups[index]

            if (group == null) {
                return 0
            } else {
                return group.value.toInt()
            }
        }

        private fun MatchResult.getStringMatch(index: Int): String {
            val group = this.groups[index]

            if (group == null) {
                return ""
            } else {
                return group.value
            }
        }

        override val descriptor: SerialDescriptor = PrimitiveDescriptor("version", PrimitiveKind.STRING)
        override fun deserialize(decoder: Decoder): Version = parse(decoder.decodeString())
        override fun serialize(encoder: Encoder, value: Version) = encoder.encodeString(value.toString())
    }
}

enum class VersionComparisonMode {
    Normal,
    DockerStyle
}
