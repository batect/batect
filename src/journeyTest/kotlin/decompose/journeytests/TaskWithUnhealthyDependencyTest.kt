package decompose.journeytests

import com.natpryce.hamkrest.assertion.assert
import com.natpryce.hamkrest.containsSubstring
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object TaskWithUnhealthyDependencyTest : Spek({
    given("a task with an unhealthy dependency") {
        val runner = ApplicationRunner("task-with-unhealthy-dependency", listOf("run", "decompose.yml", "the-task"))

        on("running that task") {
            val result = runner.run()

            it("prints an appropriate error message") {
                assert.that(result.output, containsSubstring("Dependency 'http-server' did not become healthy within the timeout period defined in that container's Dockerfile."))
            }

            it("returns a non-zero exit code") {
                assert.that(result.exitCode, !equalTo(0))
            }
        }
    }
})
