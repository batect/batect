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
import batect.testutils.equalTo
import batect.testutils.given
import batect.testutils.withColumn
import batect.testutils.withLineNumber
import batect.testutils.withMessage
import batect.utils.Json
import com.charleskorn.kaml.Yaml
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object BinarySizeSpec : Spek({
    describe("a binary size") {
        describe("creating") {
            mapOf(
                BinaryUnit.Byte to 2,
                BinaryUnit.Kilobyte to 2048,
                BinaryUnit.Megabyte to 2_097_152,
                BinaryUnit.Gigabyte to 2_147_483_648
            ).forEach { (unit, expectedBytes) ->
                given("an amount in ${unit.name.toLowerCase()}s") {
                    it("correctly calculates the equivalent number of bytes") {
                        assertThat(BinarySize.of(2, unit).bytes, equalTo(expectedBytes))
                    }
                }
            }
        }

        describe("serializing") {
            val size = BinarySize.of(2, BinaryUnit.Megabyte)

            it("serializes as the number of bytes in that size") {
                assertThat(Json.default.encodeToString(BinarySize.serializer(), size), equalTo("2097152"))
            }
        }

        describe("deserializing") {
            mapOf(
                "2" to BinarySize.of(2, BinaryUnit.Byte),
                "2b" to BinarySize.of(2, BinaryUnit.Byte),
                "2B" to BinarySize.of(2, BinaryUnit.Byte),
                "2 b" to BinarySize.of(2, BinaryUnit.Byte),
                "2 B" to BinarySize.of(2, BinaryUnit.Byte),

                "3k" to BinarySize.of(3, BinaryUnit.Kilobyte),
                "3K" to BinarySize.of(3, BinaryUnit.Kilobyte),
                "3 k" to BinarySize.of(3, BinaryUnit.Kilobyte),
                "3 K" to BinarySize.of(3, BinaryUnit.Kilobyte),
                "3kb" to BinarySize.of(3, BinaryUnit.Kilobyte),
                "3kB" to BinarySize.of(3, BinaryUnit.Kilobyte),
                "3Kb" to BinarySize.of(3, BinaryUnit.Kilobyte),
                "3KB" to BinarySize.of(3, BinaryUnit.Kilobyte),
                "3 kb" to BinarySize.of(3, BinaryUnit.Kilobyte),
                "3 kB" to BinarySize.of(3, BinaryUnit.Kilobyte),
                "3 Kb" to BinarySize.of(3, BinaryUnit.Kilobyte),
                "3 KB" to BinarySize.of(3, BinaryUnit.Kilobyte),

                "4m" to BinarySize.of(4, BinaryUnit.Megabyte),
                "4M" to BinarySize.of(4, BinaryUnit.Megabyte),
                "4 m" to BinarySize.of(4, BinaryUnit.Megabyte),
                "4 M" to BinarySize.of(4, BinaryUnit.Megabyte),
                "4mb" to BinarySize.of(4, BinaryUnit.Megabyte),
                "4mB" to BinarySize.of(4, BinaryUnit.Megabyte),
                "4Mb" to BinarySize.of(4, BinaryUnit.Megabyte),
                "4MB" to BinarySize.of(4, BinaryUnit.Megabyte),
                "4 mb" to BinarySize.of(4, BinaryUnit.Megabyte),
                "4 mB" to BinarySize.of(4, BinaryUnit.Megabyte),
                "4 Mb" to BinarySize.of(4, BinaryUnit.Megabyte),
                "4 MB" to BinarySize.of(4, BinaryUnit.Megabyte),

                "5g" to BinarySize.of(5, BinaryUnit.Gigabyte),
                "5G" to BinarySize.of(5, BinaryUnit.Gigabyte),
                "5 g" to BinarySize.of(5, BinaryUnit.Gigabyte),
                "5 G" to BinarySize.of(5, BinaryUnit.Gigabyte),
                "5gb" to BinarySize.of(5, BinaryUnit.Gigabyte),
                "5gB" to BinarySize.of(5, BinaryUnit.Gigabyte),
                "5Gb" to BinarySize.of(5, BinaryUnit.Gigabyte),
                "5GB" to BinarySize.of(5, BinaryUnit.Gigabyte),
                "5 gb" to BinarySize.of(5, BinaryUnit.Gigabyte),
                "5 gB" to BinarySize.of(5, BinaryUnit.Gigabyte),
                "5 Gb" to BinarySize.of(5, BinaryUnit.Gigabyte),
                "5 GB" to BinarySize.of(5, BinaryUnit.Gigabyte),
            ).forEach { (input, expectedSize) ->
                given("the input '$input'") {
                    it("correctly deserializes to the expected value") {
                        assertThat(Yaml.default.decodeFromString(BinarySize.serializer(), """"$input""""), equalTo(expectedSize))
                    }
                }
            }

            setOf(
                "",
                "-2",
                "2X",
                "2 X"
            ).forEach { input ->
                given("the invalid input '$input'") {
                    it("throws an exception") {
                        assertThat(
                            { Yaml.default.decodeFromString(BinarySize.serializer(), """"$input"""") },
                            throws<ConfigurationException>(withMessage("Invalid size '$input'. It must be in the format '123', '123b', '123k', '123m' or '123g'.") and withLineNumber(1) and withColumn(1))
                        )
                    }
                }
            }
        }
    }
})
