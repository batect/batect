package batect.cli

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class RequiredPositionalParameter(name: String, description: String) : PositionalParameterDefinition(name, description, false), ReadOnlyProperty<CommandDefinition, String> {
    var value: String = ""

    operator fun provideDelegate(thisRef: CommandDefinition, property: KProperty<*>): RequiredPositionalParameter {
        thisRef.requiredPositionalParameters.add(this)
        return this
    }

    override fun getValue(thisRef: CommandDefinition, property: KProperty<*>): String = value
}
