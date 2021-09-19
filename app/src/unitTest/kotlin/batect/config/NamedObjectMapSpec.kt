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

package batect.config

import batect.testutils.on
import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasSize
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object NamedObjectMapSpec : Spek({
    describe("a set of named objects") {
        describe("creating a set of named objects") {
            on("creating an empty set") {
                val set = NamedObjectMapImplementation()

                it("has no entries") {
                    assertThat(set.entries, isEmpty)
                }

                it("has no keys") {
                    assertThat(set.keys, isEmpty)
                }

                it("has no values") {
                    assertThat(set.values, isEmpty)
                }

                it("has a size of 0") {
                    assertThat(set.size, equalTo(0))
                }

                it("reports that it is empty") {
                    assertThat(set.isEmpty(), equalTo(true))
                }
            }

            on("creating a set with a single item") {
                val thing = Thing("the_thing")
                val set = NamedObjectMapImplementation(thing)

                it("has one entry") {
                    val entries = set.entries
                    assertThat(entries, hasSize(equalTo(1)))
                    assertThat(entries.map { it.key }.toSet(), equalTo(setOf(thing.name)))
                    assertThat(entries.map { it.value }.toSet(), equalTo(setOf(thing)))
                }

                it("has one key") {
                    assertThat(set.keys, equalTo(setOf(thing.name)))
                }

                it("has one value") {
                    assertThat(set.values.toList(), equalTo(listOf(thing)))
                }

                it("has a size of 1") {
                    assertThat(set.size, equalTo(1))
                }

                it("reports that it is not empty") {
                    assertThat(set.isEmpty(), equalTo(false))
                }

                it("reports that it contains the item's name") {
                    assertThat(set.containsKey(thing.name), equalTo(true))
                }

                it("reports that it contains the item") {
                    assertThat(set.containsValue(thing), equalTo(true))
                }

                it("returns the item when accessing it by name") {
                    assertThat(set[thing.name], equalTo(thing))
                }
            }

            on("creating a set with two items with different names") {
                val thing1 = Thing("thing-1")
                val thing2 = Thing("thing-2")
                val set = NamedObjectMapImplementation(thing1, thing2)

                it("has two entries") {
                    val entries = set.entries
                    assertThat(entries, hasSize(equalTo(2)))
                    assertThat(entries.map { it.key }.toSet(), equalTo(setOf(thing1.name, thing2.name)))
                    assertThat(entries.map { it.value }.toSet(), equalTo(setOf(thing1, thing2)))
                }

                it("has two keys") {
                    assertThat(set.keys, equalTo(setOf(thing1.name, thing2.name)))
                }

                it("has two values") {
                    assertThat(set.values.toList(), equalTo(listOf(thing1, thing2)))
                }

                it("has a size of 2") {
                    assertThat(set.size, equalTo(2))
                }

                it("reports that it is not empty") {
                    assertThat(set.isEmpty(), equalTo(false))
                }

                it("reports that it contains the items' names") {
                    assertThat(set.containsKey(thing1.name), equalTo(true))
                    assertThat(set.containsKey(thing2.name), equalTo(true))
                }

                it("reports that it contains the items") {
                    assertThat(set.containsValue(thing1), equalTo(true))
                    assertThat(set.containsValue(thing2), equalTo(true))
                }

                it("returns the items when accessing them by name") {
                    assertThat(set[thing1.name], equalTo(thing1))
                    assertThat(set[thing2.name], equalTo(thing2))
                }
            }

            on("creating a set with two items with the same name") {
                it("fails with an appropriate error message") {
                    val itemName = "the-item"
                    val item1 = Thing(itemName)
                    val item2 = Thing(itemName)

                    assertThat({ NamedObjectMapImplementation(item1, item2) }, throws(withMessage("Cannot create a NamedObjectMapImplementation where a thing name is used more than once. Duplicated thing names: $itemName")))
                }
            }

            on("creating a set with four items with each name duplicated") {
                it("fails with an appropriate error message") {
                    val itemName1 = "item-name-1"
                    val itemName2 = "item-name-2"
                    val item1 = Thing(itemName1)
                    val item2 = Thing(itemName1)
                    val item3 = Thing(itemName2)
                    val item4 = Thing(itemName2)

                    assertThat({ NamedObjectMapImplementation(item1, item2, item3, item4) }, throws(withMessage("Cannot create a NamedObjectMapImplementation where a thing name is used more than once. Duplicated thing names: $itemName1, $itemName2")))
                }
            }
        }
    }
})

private class NamedObjectMapImplementation(contents: Iterable<Thing>) : NamedObjectMap<Thing>("thing", contents) {
    constructor(vararg contents: Thing) : this(contents.asIterable())

    override fun nameFor(value: Thing): String = value.name
}

private data class Thing(val name: String)
