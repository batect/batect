/*
    Copyright 2017-2022 Charles Korn.

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

package batect.config.io

import batect.testutils.given
import batect.testutils.on
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ConfigurationExceptionSpec : Spek({
    describe("a configuration exception") {
        describe("formatting") {
            given("an exception with a message") {
                val exception = ConfigurationException("This is the error message", null, null, null, null)

                on("converting to a string") {
                    it("returns just the message") {
                        assertThat(exception.toString(), equalTo("This is the error message"))
                    }
                }
            }

            given("an exception with a message and a line number") {
                val exception = ConfigurationException("This is the error message", 12, null, null, null)

                on("converting to a string") {
                    it("returns the message and the line number") {
                        assertThat(exception.toString(), equalTo("Error on line 12: This is the error message"))
                    }
                }
            }

            given("an exception with a message, a line number and a column") {
                val exception = ConfigurationException("This is the error message", 12, 54, null, null)

                on("converting to a string") {
                    it("returns the message, the line number and the column") {
                        assertThat(exception.toString(), equalTo("Error on line 12, column 54: This is the error message"))
                    }
                }
            }

            given("an exception with a message, a line number, a column and a path") {
                val exception = ConfigurationException("This is the error message", 12, 54, "colours.primary.saturation", null)

                on("converting to a string") {
                    it("returns the message, the line number, the column and the path") {
                        assertThat(exception.toString(), equalTo("Error at colours.primary.saturation on line 12, column 54: This is the error message"))
                    }
                }
            }

            given("an exception with a message, a line number, a column and a cause") {
                val cause = RuntimeException("Something went wrong")
                val exception = ConfigurationException("This is the error message", 12, 54, null, cause)

                on("converting to a string") {
                    it("returns the message, the line number and the column, but not the cause") {
                        assertThat(exception.toString(), equalTo("Error on line 12, column 54: This is the error message"))
                    }
                }
            }
        }
    }
})
