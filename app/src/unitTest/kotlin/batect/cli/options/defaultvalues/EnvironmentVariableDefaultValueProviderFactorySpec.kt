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

package batect.cli.options.defaultvalues

import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object EnvironmentVariableDefaultValueProviderFactorySpec : Spek({
    describe("an environment variable default value provider") {
        val environment = mapOf(
            "SOME_VAR" to "some value"
        )

        val factory = EnvironmentVariableDefaultValueProviderFactory(environment)

        given("the source environment variable is set") {
            val provider = factory.create("SOME_VAR", "the default value")

            it("returns the value of the environment variable") {
                assertThat(provider.value, equalTo("some value"))
            }

            it("returns a description that includes the current value") {
                assertThat(provider.description, equalTo("defaults to the value of the SOME_VAR environment variable (which is currently 'some value') or 'the default value' if SOME_VAR is not set"))
            }
        }

        given("the source environment variable is not set") {
            val provider = factory.create("SOME_OTHER_VAR", "the default value")

            it("returns the default value") {
                assertThat(provider.value, equalTo("the default value"))
            }

            it("returns a description that includes the fact that the variable is not set") {
                assertThat(provider.description, equalTo("defaults to the value of the SOME_OTHER_VAR environment variable (which is currently not set) or 'the default value' if SOME_OTHER_VAR is not set"))
            }
        }
    }
})
