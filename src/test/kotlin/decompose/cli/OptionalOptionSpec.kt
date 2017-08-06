package decompose.cli

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.throws
import decompose.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object OptionalOptionSpec : Spek({
    describe("an optional option") {
        describe("creation") {
            on("attempting to create an optional option with a valid name and description") {
                it("does not throw an exception") {
                    assert.that({ OptionalOption("value", "The value.") }, !throws<Throwable>())
                }
            }

            on("attempting to create an optional option with a name with dashes") {
                it("does not throw an exception") {
                    assert.that({ OptionalOption("some-value", "The value.") }, !throws<Throwable>())
                }
            }

            on("attempting to create an optional option with an empty name") {
                it("throws an exception") {
                    assert.that({ OptionalOption("", "The value.") }, throws<IllegalArgumentException>(withMessage("Option name must not be empty.")))
                }
            }

            on("attempting to create an optional option with an empty description") {
                it("throws an exception") {
                    assert.that({ OptionalOption("value", "") }, throws<IllegalArgumentException>(withMessage("Option description must not be empty.")))
                }
            }

            on("attempting to create an optional option with a name that starts with a dash") {
                it("throws an exception") {
                    assert.that({ OptionalOption("-value", "The value.") }, throws<IllegalArgumentException>(withMessage("Option name must not start with a dash.")))
                }
            }

            on("attempting to create an optional option with a name that is only one character long") {
                it("throws an exception") {
                    assert.that({ OptionalOption("v", "The value.") }, throws<IllegalArgumentException>(withMessage("Option name must be at least two characters long.")))
                }
            }
        }
    }
})
