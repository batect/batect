package decompose.journeytests

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object HelpJourneyTest : Spek({
    given("the application") {
        on("requesting help for the application") {
            val runner = ApplicationRunner("", listOf("help"))
            val result = runner.run()

            it("prints the help header") {
                assert.that(result.output, containsSubstring("Usage: decompose COMMAND [COMMAND OPTIONS]"))
            }

            it("returns a non-zero exit code") {
                assert.that(result.exitCode, !equalTo(0))
            }
        }

        on("requesting help for a command") {
            val runner = ApplicationRunner("", listOf("help", "run"))
            val result = runner.run()

            it("prints the help header") {
                assert.that(result.output, containsSubstring("Usage: decompose run CONFIGFILE TASK"))
            }

            it("prints a description of the command") {
                assert.that(result.output, containsSubstring("Run a task."))
            }

            it("returns a non-zero exit code") {
                assert.that(result.exitCode, !equalTo(0))
            }
        }
    }
})
