package decompose.cli

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class PositionalParameter(val name: String) : ReadOnlyProperty<CommandLineCommand, String?> {
    operator fun provideDelegate(thisRef: CommandLineCommand, property: KProperty<*>): PositionalParameter {
        thisRef.positionalParameters.add(this)
        return this
    }

    override operator fun getValue(thisRef: CommandLineCommand, property: KProperty<*>): String? {
        return thisRef.positionalParameterValues[this]
    }
}
