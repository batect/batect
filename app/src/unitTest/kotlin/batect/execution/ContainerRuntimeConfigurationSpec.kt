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

package batect.execution

import batect.config.LiteralValue
import batect.config.PortMapping
import batect.os.Command
import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ContainerRuntimeConfigurationSpec : Spek({
    describe("a set of runtime configuration options for a container") {
        describe("toString()") {
            val workingDirectory = "some-working-dir"
            val additionalEnvironmentVariables = mapOf("SOME_VAR" to LiteralValue("some value"))
            val additionalPortMappings = setOf(PortMapping(123, 456))

            given("an explicit command and entrypoint are provided") {
                val command = Command.parse("the-command some-arg")
                val entrypoint = Command.parse("the-entrypoint")
                val config = ContainerRuntimeConfiguration(command, entrypoint, workingDirectory, additionalEnvironmentVariables, additionalPortMappings)

                it("returns a human-readable representation of itself") {
                    assertThat(config.toString(), equalTo("ContainerRuntimeConfiguration(command: [the-command, some-arg], entrypoint: [the-entrypoint], working directory: some-working-dir, " +
                        "additional environment variables: [SOME_VAR=LiteralValue(value: 'some value')], additional port mappings: [123:456])")
                    )
                }
            }

            given("an explicit command and entrypoint are not provided") {
                val command = null
                val entrypoint = null
                val config = ContainerRuntimeConfiguration(command, entrypoint, workingDirectory, additionalEnvironmentVariables, additionalPortMappings)

                it("returns a human-readable representation of itself") {
                    assertThat(config.toString(), equalTo("ContainerRuntimeConfiguration(command: null, entrypoint: null, working directory: some-working-dir, " +
                        "additional environment variables: [SOME_VAR=LiteralValue(value: 'some value')], additional port mappings: [123:456])")
                    )
                }
            }
        }
    }
})
