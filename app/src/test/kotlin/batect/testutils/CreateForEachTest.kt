/*
   Copyright 2017 Charles Korn.

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

import org.jetbrains.spek.api.dsl.SpecBody
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

fun <T> SpecBody.createForEachTest(creator: () -> T) : ReadOnlyProperty<Any?, T> {
    val property = object : ReadOnlyProperty<Any?, T> {
        private var value: T? = null

        fun createValue() {
            value = creator()
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            if (value == null) {
                throw IllegalStateException("Value '${property.name}' created with createForEachTest has not been initialised (are you accessing it outside of a 'beforeEachTest', 'on' or 'it' block?)")
            }

            return value!!
        }
    }

    this.beforeEachTest {
        property.createValue()
    }

    return property
}
