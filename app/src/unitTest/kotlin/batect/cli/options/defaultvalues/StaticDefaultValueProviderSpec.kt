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

import batect.testutils.equalTo
import batect.testutils.given
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.isEmptyString
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object StaticDefaultValueProviderSpec : Spek({
    describe("a static default value provider") {
        given("a non-null value") {
            val provider = StaticDefaultValueProvider("some value")

            it("provides the given value") {
                assertThat(provider.value, equalTo(PossibleValue.Valid("some value")))
            }

            it("provides a description of the default value") {
                assertThat(provider.description, equalTo("Defaults to 'some value' if not set."))
            }
        }

        given("a null value") {
            val provider = StaticDefaultValueProvider(null)

            it("provides the null as the value") {
                assertThat(provider.value, equalTo(PossibleValue.Valid(null)))
            }

            it("does not provide a description of the value") {
                assertThat(provider.description, isEmptyString)
            }
        }
    }
})
