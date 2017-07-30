package decompose.cli

import com.github.salomonbrys.kodein.Kodein
import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.throws
import decompose.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object CommandDefinitionSpec : Spek({
    fun createCommandDefinition(name: String, description: String): CommandDefinition = object : CommandDefinition(name, description) {
        override fun createCommand(kodein: Kodein): Command = throw NotImplementedError()
    }

    describe("a command definition") {
        describe("creation") {
            on("attempting to create a command definition with a name and description") {
                it("does not throw an exception") {
                    assert.that({ createCommandDefinition("do-stuff", "Do the thing.") }, !throws<Throwable>())
                }
            }

            on("attempting to create a command definition with an empty name") {
                it("throws an exception") {
                    assert.that({ createCommandDefinition("", "Do the thing.") }, throws<IllegalArgumentException>(withMessage("Command name must not be empty.")))
                }
            }

            on("attempting to create a command definition with an empty description") {
                it("throws an exception") {
                    assert.that({ createCommandDefinition("do-stuff", "") }, throws<IllegalArgumentException>(withMessage("Command description must not be empty.")))
                }
            }
        }
    }
})
