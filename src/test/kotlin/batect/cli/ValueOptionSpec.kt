package batect.cli

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ValueOptionSpec : Spek({
    describe("a value option") {
        on("not applying a value for the option") {
            val option = ValueOption("value", "Some value")

            it("returns 'null' as the value") {
                assertThat(option.value, absent())
            }
        }

        on("applying a value for the option") {
            val option = ValueOption("value", "Some value")
            option.applyValue("abc123")

            it("returns that value as the value") {
                assertThat(option.value, equalTo("abc123"))
            }
        }
    }
})
