package decompose.cli

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class PositionalParameter(val name: String) : ReadOnlyProperty<CommandDefinition, String?> {
    operator fun provideDelegate(thisRef: CommandDefinition, property: KProperty<*>): PositionalParameter {
        thisRef.positionalParameters.add(this)
        return this
    }

    override operator fun getValue(thisRef: CommandDefinition, property: KProperty<*>): String? {
        return thisRef.positionalParameterValues[this]
    }
}
