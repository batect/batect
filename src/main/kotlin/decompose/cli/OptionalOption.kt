package decompose.cli

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class OptionalOption(val name: String, val description: String) : ReadOnlyProperty<OptionParserContainer, String?> {
    var value: String? = null

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
    }

    operator fun provideDelegate(thisRef: OptionParserContainer, property: KProperty<*>): OptionalOption {
        thisRef.optionParser.addOptionalOption(this)
        return this
    }

    override fun getValue(thisRef: OptionParserContainer, property: KProperty<*>): String? = value
}
