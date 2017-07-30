package decompose.cli

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.throws
import decompose.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object PositionalParameterDefinitionSpec : Spek({
    describe("a positional parameter") {
        describe("creation") {
            on("attempting to create a positional parameter with an uppercase name") {
                it("does not throw an exception") {
                    assert.that({ PositionalParameterDefinition("THING", "The thing.") }, !throws<Throwable>())
                }
            }

            on("attempting to create a positional parameter with a lowercase name") {
                it("throws an exception") {
                    assert.that({ PositionalParameterDefinition("thing", "The thing.") }, throws<IllegalArgumentException>(withMessage("Positional parameter name must be all uppercase.")))
                }
            }

            on("attempting to create a positional parameter with a mixed-case name") {
                it("throws an exception") {
                    assert.that({ PositionalParameterDefinition("thInG", "The thing.") }, throws<IllegalArgumentException>(withMessage("Positional parameter name must be all uppercase.")))
                }
            }

            on("attempting to create a positional parameter with an empty name") {
                it("throws an exception") {
                    assert.that({ PositionalParameterDefinition("", "The thing.") }, throws<IllegalArgumentException>(withMessage("Positional parameter name must not be empty.")))
                }
            }
        }
    }
})
