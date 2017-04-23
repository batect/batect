package decompose.config.io

data class ConfigurationException(
        override val message: String,
        val fileName: String,
        val lineNumber: Int?,
        val column: Int?,
        override val cause: Throwable) : Exception(message, cause) {

    override fun toString(): String {
        val location = if (lineNumber != null && column != null) {
            "$fileName (line $lineNumber, column $column)"
        } else if (lineNumber != null) {
            "$fileName (line $lineNumber)"
        } else {
            fileName
        }

        return "$location: $message\nCaused by: $cause"
    }
}
