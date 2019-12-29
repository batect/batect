/*
   Copyright 2017-2019 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect

import batect.cli.CommandLineOptions
import batect.execution.ConfigVariablesProvider
import batect.logging.LogSink
import batect.testutils.InMemoryLogSink
import batect.testutils.doesNotThrow
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import org.kodein.di.Copy
import org.kodein.di.Kodein
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object InjectionConfigurationSpec : Spek({
    describe("Kodein injection configuration") {
        val originalConfiguration = createKodeinConfiguration(PrintStream(ByteArrayOutputStream()), PrintStream(ByteArrayOutputStream()), ByteArrayInputStream(ByteArray(0)))
        val configuration = Kodein.direct {
            extend(originalConfiguration, copy = Copy.All)
            bind<CommandLineOptions>() with instance(CommandLineOptions(taskName = "test-task"))
            bind<ConfigVariablesProvider>() with instance(ConfigVariablesProvider(emptyMap(), null))
            bind<LogSink>() with instance(InMemoryLogSink())
        }

        describe("each registered class") {
            configuration.container.tree.bindings.keys.forEach { key ->
                on("attempting to get an instance matching $key") {
                    it("does not throw an invalid configuration exception") {
                        assertThat({ configuration.Instance(key.type, key.tag) }, doesNotThrow())
                    }
                }
            }
        }
    }
})
