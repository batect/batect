package decompose.cli

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class OptionalOption(val name: String, val description: String) : ReadOnlyProperty<OptionParserContainer, String?> {
    var value: String? = null

    operator fun provideDelegate(thisRef: OptionParserContainer, property: KProperty<*>): OptionalOption {
        thisRef.optionParser.addOptionalOption(this)
        return this
    }

    override fun getValue(thisRef: OptionParserContainer, property: KProperty<*>): String? = value
}
