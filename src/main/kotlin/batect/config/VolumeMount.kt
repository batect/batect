package batect.config

import com.fasterxml.jackson.annotation.JsonCreator

data class VolumeMount(val localPath: String, val containerPath: String) {
    override fun toString(): String {
        return "$localPath:$containerPath"
    }

    companion object {
        @JvmStatic @JsonCreator
        fun parse(value: String): VolumeMount {
            if (value == "") {
                throw IllegalArgumentException("Volume mount definition cannot be empty.")
            }

            val separator = ':'
            val separatorIndex = value.indexOf(separator)

            if (separatorIndex == -1) {
                throw invalidMountDefinitionException(value)
            }

            val local = value.substring(0, separatorIndex)
            val container = value.substring(separatorIndex + 1)

            if (local == "" || container == "") {
                throw invalidMountDefinitionException(value)
            }

            return VolumeMount(local, container)
        }

        fun invalidMountDefinitionException(value: String): Throwable = IllegalArgumentException("Volume mount definition '$value' is not valid. It must be in the form 'local_path:container_path'.")
    }
}
