/*
    Copyright 2017-2021 Charles Korn.

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
import batect.testutils.withPath
import com.charleskorn.kaml.Location
import com.charleskorn.kaml.YamlPath
import com.natpryce.hamkrest.and
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.throws
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TaskMapSpec : Spek({
    describe("a task map") {
        describe("validating task names") {
            setOf(
                "a",
                "A",
                "1",
                "a.a",
                "a-a",
                "a1",
                "1a",
                "a_a",
                "a:a"
            ).forEach { name ->
                given("the valid name '$name'") {
                    it("does not throw an exception") {
                        assertThat({ TaskMap.validateName(name, YamlPath.root) }, doesNotThrow())
                    }
                }
            }

            setOf(
                "",
                ".",
                "-",
                "_",
                ":",
                ".a",
                "-a",
                "_a",
                ":a",
                "a.",
                "a-",
                "a_",
                "a:",
                "a!a",
                "a\\a"
            ).forEach { name ->
                given("the invalid name '$name'") {
                    it("throws an appropriate exception") {
                        assertThat(
                            { TaskMap.validateName(name, YamlPath.root.withListEntry(0, Location(2, 3))) },
                            throws<ConfigurationException>(withMessage("Invalid task name '$name'. Task names must contain only letters, digits, colons, dashes, periods and underscores, and must start and end with a letter or digit.") and withLineNumber(2) and withColumn(3) and withPath("[0]"))
                        )
                    }
                }
            }
        }
    }
})
