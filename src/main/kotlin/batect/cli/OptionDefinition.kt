package batect.cli

abstract class OptionDefinition(val name: String,
                                val description: String,
                                val shortName: Char? = null) {
    var valueHasBeenSet: Boolean = false

    val longOption = "--$name"
    val shortOption = if (shortName != null) "-$shortName" else null

    init {
        if (name == "") {
            throw IllegalArgumentException("Option name must not be empty.")
        }

        if (name.startsWith("-")) {
            throw IllegalArgumentException("Option name must not start with a dash.")
        }

        if (name.length < 2) {
            throw IllegalArgumentException("Option name must be at least two characters long.")
        }

        if (description == "") {
            throw IllegalArgumentException("Option description must not be empty.")
        }

        if (shortName != null && !shortName.isLetterOrDigit()) {
            throw IllegalArgumentException("Option short name must be alphanumeric.")
        }
    }

    abstract fun applyValue(newValue: String)

    open fun reset() {}
}
