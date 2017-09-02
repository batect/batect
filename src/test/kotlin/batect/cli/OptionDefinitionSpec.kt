package batect.cli

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import batect.testutils.withMessage
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object OptionDefinitionSpec : Spek({
    describe("a option definition") {
        describe("creation") {
            fun createOption(name: String, description: String, shortName: Char? = null): OptionDefinition {
                return object : OptionDefinition(name, description, shortName) {
                    override fun applyValue(newValue: String) = throw NotImplementedError()
                }
            }

            on("attempting to create an value option with a valid name and description") {
                it("does not throw an exception") {
                    assertThat({ createOption("value", "The value.") }, !throws<Throwable>())
                }
            }

            on("attempting to create an value option with a valid name, description and short name") {
                it("does not throw an exception") {
                    assertThat({ createOption("value", "The value.", 'v') }, !throws<Throwable>())
                }
            }

            on("attempting to create an value option with a name with dashes") {
                it("does not throw an exception") {
                    assertThat({ createOption("some-value", "The value.") }, !throws<Throwable>())
                }
            }

            on("attempting to create an value option with an empty name") {
                it("throws an exception") {
                    assertThat({ createOption("", "The value.") }, throws<IllegalArgumentException>(withMessage("Option name must not be empty.")))
                }
            }

            on("attempting to create an value option with an empty description") {
                it("throws an exception") {
                    assertThat({ createOption("value", "") }, throws<IllegalArgumentException>(withMessage("Option description must not be empty.")))
                }
            }

            on("attempting to create an value option with a name that starts with a dash") {
                it("throws an exception") {
                    assertThat({ createOption("-value", "The value.") }, throws<IllegalArgumentException>(withMessage("Option name must not start with a dash.")))
                }
            }

            on("attempting to create an value option with a name that is only one character long") {
                it("throws an exception") {
                    assertThat({ createOption("v", "The value.") }, throws<IllegalArgumentException>(withMessage("Option name must be at least two characters long.")))
                }
            }

            on("attempting to create an value option with a short name that is a dash") {
                it("does not throw an exception") {
                    assertThat({ createOption("value", "The value.", '-') }, throws<IllegalArgumentException>(withMessage("Option short name must be alphanumeric.")))
                }
            }

            on("attempting to create an value option with a short name that is a space") {
                it("does not throw an exception") {
                    assertThat({ createOption("value", "The value.", ' ') }, throws<IllegalArgumentException>(withMessage("Option short name must be alphanumeric.")))
                }
            }
        }
    }
})
