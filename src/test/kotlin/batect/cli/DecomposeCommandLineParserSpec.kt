package batect.cli

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.instance
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object DecomposeCommandLineParserSpec : Spek({
    describe("a Decompose-specific command line parser") {
        on("creating bindings for common options") {
            val emptyKodein = Kodein {}
            val parser = DecomposeCommandLineParser(emptyKodein)
            val bindings = Kodein {
                import(parser.createBindings())
            }

            it("includes the value of the configuration file name") {
                assertThat(bindings.instance<String>(CommonOptions.ConfigurationFileName), equalTo("decompose.yml"))
            }
        }
    }
})
