/*
   Copyright 2017 Charles Korn.

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

data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int {
        if (major != other.major) {
            return major.compareTo(other.major)
        } else if (minor != other.minor) {
            return minor.compareTo(other.minor)
        } else {
            return patch.compareTo(other.patch)
        }
    }

    override fun toString(): String {
        if (patch == 0) {
            return "$major.$minor"
        } else {
            return "$major.$minor.$patch"
        }
    }

    companion object {
        private val regex = """^(?<major>\d+)(\.(?<minor>\d+)(\.(?<patch>\d+))?)?$""".toRegex()

        fun parse(value: String): Version {
            val match = regex.matchEntire(value)

            if (match == null) {
                throw IllegalArgumentException("The value '$value' is not recognised as a valid version.")
            }

            val major = match.getIntegerMatch(1)
            val minor = match.getIntegerMatch(3)
            val patch = match.getIntegerMatch(5)

            return Version(major, minor, patch)
        }

        private fun MatchResult.getIntegerMatch(index: Int, default: Int = 0): Int {
            val group = this.groups[index]

            if (group == null) {
                return default
            } else {
                return group.value.toInt()
            }
        }
    }
}
