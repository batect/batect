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

package batect.os

import batect.testutils.equalTo
import com.natpryce.hamkrest.assertion.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DimensionsSpec : Spek({
    describe("a set of dimensions") {
        describe("adding dimensions") {
            val first = Dimensions(50, 60)
            val second = Dimensions(5, 6)

            it("adds the widths and heights") {
                assertThat(first + second, equalTo(Dimensions(55, 66)))
            }
        }

        describe("subtracting dimensions") {
            val first = Dimensions(50, 60)
            val second = Dimensions(5, 6)

            it("subtracts the widths and heights") {
                assertThat(first - second, equalTo(Dimensions(45, 54)))
            }
        }
    }
})
