package batect.cli

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class ValueOption(name: String,
                  description: String,
                  shortName: Char? = null
) : OptionDefinition(name, description, shortName), ReadOnlyProperty<OptionParserContainer, String?> {

    var value: String? = null

    override fun applyValue(newValue: String) {
        value = newValue
    }

    operator fun provideDelegate(thisRef: OptionParserContainer, property: KProperty<*>): ValueOption {
        thisRef.optionParser.addOption(this)
        return this
    }

    override fun getValue(thisRef: OptionParserContainer, property: KProperty<*>): String? = value
}
