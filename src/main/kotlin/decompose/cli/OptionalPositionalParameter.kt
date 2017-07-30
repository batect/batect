package decompose.cli

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class OptionalPositionalParameter(name: String, description: String) : PositionalParameterDefinition(name, description), ReadOnlyProperty<CommandDefinition, String?> {
    var value: String? = null

    operator fun provideDelegate(thisRef: CommandDefinition, property: KProperty<*>): OptionalPositionalParameter {
        thisRef.optionalPositionalParameters.add(this)
        return this
    }

    override fun getValue(thisRef: CommandDefinition, property: KProperty<*>): String? = value
}
