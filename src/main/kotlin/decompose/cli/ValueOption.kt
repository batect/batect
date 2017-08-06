package decompose.cli

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class ValueOption(val name: String,
                  val description: String,
                  val shortName: Char? = null,
                  val defaultValue: String? = null
) : ReadOnlyProperty<OptionParserContainer, String?> {
    var value: String? = defaultValue
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

        reset()
    }

    fun reset() {
        value = defaultValue
        valueHasBeenSet = false
    }

    operator fun provideDelegate(thisRef: OptionParserContainer, property: KProperty<*>): ValueOption {
        thisRef.optionParser.addOption(this)
        return this
    }

    override fun getValue(thisRef: OptionParserContainer, property: KProperty<*>): String? = value
}
