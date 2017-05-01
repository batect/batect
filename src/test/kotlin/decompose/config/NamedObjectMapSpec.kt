package decompose.config

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.hasSize
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.throws
import decompose.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object NamedObjectMapSpec : Spek({
    describe("a set of named objects") {
        describe("creating a set of named objects") {
            on("creating an empty set") {
                val set = NamedObjectMapImplementation()

                it("has no entries") {
                    assert.that(set.entries, isEmpty)
                }

                it("has no keys") {
                    assert.that(set.keys, isEmpty)
                }

                it("has no values") {
                    assert.that(set.values, isEmpty)
                }

                it("has a size of 0") {
                    assert.that(set.size, equalTo(0))
                }

                it("reports that it is empty") {
                    assert.that(set.isEmpty(), equalTo(true))
                }
            }

            on("creating a set with a single item") {
                val thing = Thing("the_thing")
                val set = NamedObjectMapImplementation(thing)

                it("has one entry") {
                    val entries = set.entries
                    assert.that(entries, hasSize(equalTo(1)))
                    assert.that(entries.map { it.key }.toSet(), equalTo(setOf(thing.name)))
                    assert.that(entries.map { it.value }.toSet(), equalTo(setOf(thing)))
                }

                it("has one key") {
                    assert.that(set.keys, equalTo(setOf(thing.name)))
                }

                it("has one value") {
                    assert.that(set.values.toList(), equalTo(listOf(thing)))
                }

                it("has a size of 1") {
                    assert.that(set.size, equalTo(1))
                }

                it("reports that it is not empty") {
                    assert.that(set.isEmpty(), equalTo(false))
                }

                it("reports that it contains the item's name") {
                    assert.that(set.containsKey(thing.name), equalTo(true))
                }

                it("reports that it contains the item") {
                    assert.that(set.containsValue(thing), equalTo(true))
                }

                it("returns the item when accessing it by name") {
                    assert.that(set[thing.name], equalTo(thing))
                }
            }

            on("creating a set with two items with different names") {
                val thing1 = Thing("thing-1")
                val thing2 = Thing("thing-2")
                val set = NamedObjectMapImplementation(thing1, thing2)

                it("has two entries") {
                    val entries = set.entries
                    assert.that(entries, hasSize(equalTo(2)))
                    assert.that(entries.map { it.key }.toSet(), equalTo(setOf(thing1.name, thing2.name)))
                    assert.that(entries.map { it.value }.toSet(), equalTo(setOf(thing1, thing2)))
                }

                it("has two keys") {
                    assert.that(set.keys, equalTo(setOf(thing1.name, thing2.name)))
                }

                it("has two values") {
                    assert.that(set.values.toList(), equalTo(listOf(thing1, thing2)))
                }

                it("has a size of 2") {
                    assert.that(set.size, equalTo(2))
                }

                it("reports that it is not empty") {
                    assert.that(set.isEmpty(), equalTo(false))
                }

                it("reports that it contains the items' names") {
                    assert.that(set.containsKey(thing1.name), equalTo(true))
                    assert.that(set.containsKey(thing2.name), equalTo(true))
                }

                it("reports that it contains the items") {
                    assert.that(set.containsValue(thing1), equalTo(true))
                    assert.that(set.containsValue(thing2), equalTo(true))
                }

                it("returns the items when accessing them by name") {
                    assert.that(set[thing1.name], equalTo(thing1))
                    assert.that(set[thing2.name], equalTo(thing2))
                }
            }

            on("creating a set with two items with the same name") {
                it("fails with an appropriate error message") {
                    val itemName = "the-item"
                    val item1 = Thing(itemName)
                    val item2 = Thing(itemName)

                    assert.that({ NamedObjectMapImplementation(item1, item2) }, throws(withMessage("Cannot create a NamedObjectMapImplementation where a thing name is used more than once. Duplicated thing names: $itemName")))
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

                    assert.that({ NamedObjectMapImplementation(item1, item2, item3, item4) }, throws(withMessage("Cannot create a NamedObjectMapImplementation where a thing name is used more than once. Duplicated thing names: $itemName1, $itemName2")))
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
