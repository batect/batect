package decompose.cli

open class PositionalParameterDefinition(val name: String, val description: String, val isOptional: Boolean) {
    init {
        if (name == "") {
            throw IllegalArgumentException("Positional parameter name must not be empty.")
        }

        if (name != name.toUpperCase()) {
            throw IllegalArgumentException("Positional parameter name must be all uppercase.")
        }

        if (description == "") {
            throw IllegalArgumentException("Positional parameter description must not be empty.")
        }
    }
}
