package decompose.journeytests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ContainerWithDependencyChainJourneyTest : Spek({
    given("a task with a container with a chain of dependencies") {
        val runner = ApplicationRunner("container-with-dependency-chain", listOf("run", "the-task"))

        on("running that task") {
            val result = runner.run()

            it("displays the output from that task") {
                assertThat(result.output, containsSubstring("Status code for request: 200"))
            }

            it("returns the exit code from that task") {
                assertThat(result.exitCode, equalTo(0))
            }
        }
    }
})
