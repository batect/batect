/*
   Copyright 2017-2021 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.testutils

import org.spekframework.spek2.dsl.LifecycleAware
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

private fun <T : Any> LifecycleAware.produceNonNullValueForEachTest(description: String, scope: ValueScope, creator: () -> T, throwImmediately: Boolean = false): ReadOnlyProperty<Any?, T> {
    val property = object : ReadOnlyProperty<Any?, T> {
        private var value: T? = null
        private var exceptionThrown: Throwable? = null

        fun createValue() {
            try {
                value = creator()
                exceptionThrown = null
            } catch (t: Throwable) {
                value = null
                exceptionThrown = t

                if (throwImmediately) {
                    throw t
                }
            }
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            if (value == null) {
                if (exceptionThrown != null) {
                    throw IllegalStateException("Value '${property.name}' created with $description could not be initialised.", exceptionThrown)
                }

                throw IllegalStateException("Value '${property.name}' created with $description has not been initialised (are you accessing it outside of a '${scope.beforeEachFunctionName}' or 'it' block?)")
            }

            return value!!
        }
    }

    when (scope) {
        ValueScope.EachTest -> this.beforeEachTest {
            property.createValue()
        }
        ValueScope.Group -> this.beforeGroup {
            property.createValue()
        }
    }

    return property
}

// FIXME: Ideally we would combine this with the function above but I can't find a way to make it null-safe in both cases.
private fun <T : Any?> LifecycleAware.produceNullableValueForEachTest(description: String, scope: ValueScope, creator: () -> T?, throwImmediately: Boolean = false): ReadOnlyProperty<Any?, T?> {
    val property = object : ReadOnlyProperty<Any?, T?> {
        private var valueInitialised = false
        private var value: T? = null
        private var exceptionThrown: Throwable? = null

        fun createValue() {
            try {
                value = creator()
                valueInitialised = true
                exceptionThrown = null
            } catch (t: Throwable) {
                value = null
                valueInitialised = false
                exceptionThrown = t

                if (throwImmediately) {
                    throw t
                }
            }
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T? {
            if (!valueInitialised) {
                if (exceptionThrown != null) {
                    throw IllegalStateException("Value '${property.name}' created with $description could not be initialised.", exceptionThrown)
                }

                throw IllegalStateException("Value '${property.name}' created with $description has not been initialised (are you accessing it outside of a '${scope.beforeEachFunctionName}' or 'it' block?)")
            }

            return value
        }
    }

    when (scope) {
        ValueScope.EachTest -> this.beforeEachTest {
            property.createValue()
        }
        ValueScope.Group -> this.beforeGroup {
            property.createValue()
        }
    }

    return property
}

private enum class ValueScope(val beforeEachFunctionName: String) {
    EachTest("beforeEachTest"),
    Group("beforeGroup")
}

fun <T : Any> LifecycleAware.createForEachTest(creator: () -> T): ReadOnlyProperty<Any?, T> = produceNonNullValueForEachTest("createForEachTest", ValueScope.EachTest, creator)
fun <T : Any> LifecycleAware.runForEachTest(creator: () -> T): ReadOnlyProperty<Any?, T> = produceNonNullValueForEachTest("runForEachTest", ValueScope.EachTest, creator, true)
fun <T : Any?> LifecycleAware.runNullableForEachTest(creator: () -> T?): ReadOnlyProperty<Any?, T?> = produceNullableValueForEachTest("runNullableForEachTest", ValueScope.EachTest, creator, true)

fun <T : Any> LifecycleAware.createForGroup(creator: () -> T): ReadOnlyProperty<Any?, T> = produceNonNullValueForEachTest("createForGroup", ValueScope.Group, creator)
fun <T : Any> LifecycleAware.runBeforeGroup(creator: () -> T): ReadOnlyProperty<Any?, T> = produceNonNullValueForEachTest("runBeforeGroup", ValueScope.Group, creator, true)
