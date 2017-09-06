package batect.cli

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ValueOptionWithDefaultSpec : Spek({
    describe("a value option with a default value") {
        on("not applying a value for the option") {
            val option = ValueOptionWithDefault("value", "Some value", "some-default")

            it("returns the default value as the value") {
                assertThat(option.value, equalTo("some-default"))
            }
        }

        on("applying a value for the option") {
            val option = ValueOptionWithDefault("value", "Some value", "some-default")
            option.applyValue("abc123")

            it("returns that value as the value") {
                assertThat(option.value, equalTo("abc123"))
            }
        }
    }
})
