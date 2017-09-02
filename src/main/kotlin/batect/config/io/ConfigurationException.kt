package batect.config.io

data class ConfigurationException(
        override val message: String,
        val fileName: String?,
        val lineNumber: Int?,
        val column: Int?,
        override val cause: Throwable?) : RuntimeException(message, cause) {

    constructor(message: String) : this(message, null, null, null, null)

    override fun toString(): String {
        val location = locationString()
        val causeDescription = if (cause != null) "\nCaused by: $cause" else ""
        val description = message + causeDescription

        if (location != "") {
            return "$location: $description"
        } else {
            return description
        }
    }

    private fun locationString() = when {
        fileName != null && lineNumber != null && column != null -> "$fileName (line $lineNumber, column $column)"
        fileName != null && lineNumber != null -> "$fileName (line $lineNumber)"
        fileName != null -> fileName
        else -> ""
    }
}
