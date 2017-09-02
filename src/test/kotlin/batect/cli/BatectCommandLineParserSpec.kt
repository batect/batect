package batect.cli

import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.instance
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object BatectCommandLineParserSpec : Spek({
    describe("a batect-specific command line parser") {
        on("creating bindings for common options") {
            val emptyKodein = Kodein {}
            val parser = BatectCommandLineParser(emptyKodein)
            val bindings = Kodein {
                import(parser.createBindings())
            }

            it("includes the value of the configuration file name") {
                assertThat(bindings.instance<String>(CommonOptions.ConfigurationFileName), equalTo("batect.yml"))
            }
        }
    }
})
