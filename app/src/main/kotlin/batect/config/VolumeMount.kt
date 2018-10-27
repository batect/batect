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

package batect.config

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class VolumeMount(
    @JsonProperty("local") val localPath: String,
    @JsonProperty("container") val containerPath: String,
    val options: String?
) {
    override fun toString(): String {
        if (options == null) {
            return "$localPath:$containerPath"
        } else {
            return "$localPath:$containerPath:$options"
        }
    }

    companion object {
        @JvmStatic
        @JsonCreator
        fun parse(value: String): VolumeMount {
            if (value == "") {
                throw IllegalArgumentException("Volume mount definition cannot be empty.")
            }

            val parts = value.split(':')

            if (parts.size < 2 || parts.size > 3) {
                throw invalidMountDefinitionException(value)
            }

            val local = parts[0]
            val container = parts[1]
            val options = parts.getOrNull(2)

            if (local == "" || container == "" || options == "") {
                throw invalidMountDefinitionException(value)
            }

            return VolumeMount(local, container, options)
        }

        fun invalidMountDefinitionException(value: String): Throwable = IllegalArgumentException("Volume mount definition '$value' is not valid. It must be in the form 'local_path:container_path' or 'local_path:container_path:options'.")
    }
}
