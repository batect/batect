package batect.config

import com.fasterxml.jackson.annotation.JsonCreator

data class VolumeMount(val localPath: String, val containerPath: String, val options: String?) {
    override fun toString(): String {
        if (options == null) {
            return "$localPath:$containerPath"
        } else {
            return "$localPath:$containerPath:$options"
        }
    }

    companion object {
        @JvmStatic @JsonCreator
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
