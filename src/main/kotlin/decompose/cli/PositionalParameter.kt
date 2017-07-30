package decompose.cli

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class PositionalParameter(val name: String, val description: String) : ReadOnlyProperty<CommandDefinition, String?> {
    init {
        if (name != name.toUpperCase()) {
            throw IllegalArgumentException("Positional parameter name must be all uppercase.")
        }
    }

    operator fun provideDelegate(thisRef: CommandDefinition, property: KProperty<*>): PositionalParameter {
        thisRef.positionalParameters.add(this)
        return this
    }

    override operator fun getValue(thisRef: CommandDefinition, property: KProperty<*>): String? {
        return thisRef.positionalParameterValues[this]
    }
}
