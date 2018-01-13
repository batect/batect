/*
   Copyright 2017-2018 Charles Korn.

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

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on

object ConfigurationExceptionSpec : Spek({
    describe("a configuration exception") {
        describe("formatting") {
            given("an exception with just a message") {
                val exception = ConfigurationException("This is the error message")

                on("converting to a string") {
                    it("returns just the message") {
                        assertThat(exception.toString(), equalTo("This is the error message"))
                    }
                }
            }

            given("an exception with a message and a cause") {
                val cause = RuntimeException("Something went wrong")
                val exception = ConfigurationException("This is the error message", null, null, null, cause)

                on("converting to a string") {
                    it("returns the message and details of the cause") {
                        assertThat(exception.toString(), equalTo("This is the error message\nCaused by: java.lang.RuntimeException: Something went wrong"))
                    }
                }
            }

            given("an exception with a message and a file name") {
                val exception = ConfigurationException("This is the error message", "source.txt", null, null, null)

                on("converting to a string") {
                    it("returns the message and the file name") {
                        assertThat(exception.toString(), equalTo("source.txt: This is the error message"))
                    }
                }
            }

            given("an exception with a message, a file name and a line number") {
                val exception = ConfigurationException("This is the error message", "source.txt", 12, null, null)

                on("converting to a string") {
                    it("returns the message, the file name and the line number") {
                        assertThat(exception.toString(), equalTo("source.txt (line 12): This is the error message"))
                    }
                }
            }

            given("an exception with a message, a file name, a line number and a column") {
                val exception = ConfigurationException("This is the error message", "source.txt", 12, 54, null)

                on("converting to a string") {
                    it("returns the message, the file name, the line number and the column") {
                        assertThat(exception.toString(), equalTo("source.txt (line 12, column 54): This is the error message"))
                    }
                }
            }

            given("an exception with a message, a file name, a line number, a column and a cause") {
                val cause = RuntimeException("Something went wrong")
                val exception = ConfigurationException("This is the error message", "source.txt", 12, 54, cause)

                on("converting to a string") {
                    it("returns the message, the file name, the line number, the column and the cause") {
                        assertThat(exception.toString(), equalTo("source.txt (line 12, column 54): This is the error message\nCaused by: java.lang.RuntimeException: Something went wrong"))
                    }
                }
            }
        }
    }
})
