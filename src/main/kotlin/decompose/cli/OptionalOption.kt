package decompose.cli

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class OptionalOption(val name: String, val description: String) : ReadOnlyProperty<CommandLineParser, String?> {
    var value: String? = null

    operator fun provideDelegate(thisRef: CommandLineParser, property: KProperty<*>): OptionalOption {
        thisRef.addOptionalOption(this)
        return this
    }

    override fun getValue(thisRef: CommandLineParser, property: KProperty<*>): String? = value
}
