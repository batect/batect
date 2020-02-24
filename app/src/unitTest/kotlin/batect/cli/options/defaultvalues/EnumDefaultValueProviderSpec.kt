/*
   Copyright 2017-2020 Charles Korn.

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

import batect.execution.CacheType
import batect.testutils.equalTo
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object EnumDefaultValueProviderSpec : Spek({
    describe("a enum default value provider") {
        val provider = EnumDefaultValueProvider(CacheType.Volume)

        it("provides the given value") {
            assertThat(provider.value, equalTo(PossibleValue.Valid(CacheType.Volume)))
        }

        it("provides a description of the default value with the enum value in lowercase") {
            assertThat(provider.description, equalTo("Defaults to 'volume' if not set."))
        }
    }
})
