package batect.testutils

import org.jetbrains.spek.api.dsl.SpecBody
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

data class CreateForEachTest<T>(val spec: SpecBody, val creator: () -> T) : ReadOnlyProperty<Any?, T> {
    private var value: T? = null

    init {
        spec.beforeEachTest {
            value = creator()
        }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (value == null) {
            throw IllegalStateException("Value '${property.name}' created with CreateForEachTest has not been initialised (are you accessing it outside of a 'beforeEachTest', 'on' or 'it' block?)")
        }

        return value!!
    }
}
