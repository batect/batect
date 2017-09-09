/*
   Copyright 2017 Charles Korn.

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

package batect.cli.commands

import batect.testutils.withMessage
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object PositionalParameterDefinitionSpec : Spek({
    describe("a positional parameter") {
        describe("creation") {
            on("attempting to create a positional parameter with an uppercase name and description") {
                it("does not throw an exception") {
                    assertThat({ PositionalParameterDefinition("THING", "The thing.", true) }, !throws<Throwable>())
                }
            }

            on("attempting to create a positional parameter with a lowercase name") {
                it("throws an exception") {
                    assertThat({ PositionalParameterDefinition("thing", "The thing.", true) }, throws<IllegalArgumentException>(withMessage("Positional parameter name must be all uppercase.")))
                }
            }

            on("attempting to create a positional parameter with a mixed-case name") {
                it("throws an exception") {
                    assertThat({ PositionalParameterDefinition("thInG", "The thing.", true) }, throws<IllegalArgumentException>(withMessage("Positional parameter name must be all uppercase.")))
                }
            }

            on("attempting to create a positional parameter with an empty name") {
                it("throws an exception") {
                    assertThat({ PositionalParameterDefinition("", "The thing.", true) }, throws<IllegalArgumentException>(withMessage("Positional parameter name must not be empty.")))
                }
            }

            on("attempting to create a positional parameter without a description") {
                it("throws an exception") {
                    assertThat({ PositionalParameterDefinition("THING", "", true) }, throws<IllegalArgumentException>(withMessage("Positional parameter description must not be empty.")))
                }
            }
        }
    }
})
