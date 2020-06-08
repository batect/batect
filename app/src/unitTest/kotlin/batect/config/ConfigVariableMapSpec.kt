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

package batect.config

import batect.config.io.ConfigurationException
import batect.testutils.doesNotThrow
import batect.testutils.given
import batect.testutils.withColumn
import batect.testutils.withLineNumber
import batect.testutils.withMessage
import com.charleskorn.kaml.Location
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ConfigVariableMapSpec : Spek({
    describe("a config variable map") {
        describe("validating config variable names") {
            setOf(
                "a",
                "A",
                "a.a",
                "a-a",
                "a1",
                "a_a"
            ).forEach { name ->
                given("the valid name '$name'") {
                    it("does not throw an exception") {
                        assertThat({ ConfigVariableMap.validateName(name, Location(2, 3)) }, doesNotThrow())
                    }
                }
            }

            setOf(
                "",
                ".",
                "-",
                "1",
                "_",
                "batect",
                "batecta",
                "batect.blah",
                "BATECT",
                "BATECTa",
                "BATECT.blah",
                ".a",
                "-a",
                "1a",
                "_a"
            ).forEach { name ->
                given("the invalid name '$name'") {
                    it("throws an appropriate exception") {
                        assertThat(
                            { ConfigVariableMap.validateName(name, Location(2, 3)) },
                            throws<ConfigurationException>(withMessage("Invalid config variable name '$name'. Config variable names must start with a letter, contain only letters, digits, dashes, periods and underscores, and must not start with 'batect'.") and withLineNumber(2) and withColumn(3))
                        )
                    }
                }
            }
        }
    }
})
