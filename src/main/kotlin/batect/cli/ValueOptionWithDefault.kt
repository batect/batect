package batect.cli

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class ValueOptionWithDefault(name: String,
                             description: String,
                             val defaultValue: String,
                             shortName: Char? = null
) : OptionDefinition(name, description, shortName), ReadOnlyProperty<OptionParserContainer, String> {

    var value: String = defaultValue

    override fun applyValue(newValue: String) {
        value = newValue
    }

    operator fun provideDelegate(thisRef: OptionParserContainer, property: KProperty<*>): ValueOptionWithDefault {
        thisRef.optionParser.addOption(this)
        return this
    }

    override fun getValue(thisRef: OptionParserContainer, property: KProperty<*>): String = value
}
